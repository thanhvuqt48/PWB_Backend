package com.fpt.producerworkbench.service.impl;

import com.fpt.producerworkbench.dto.request.PortfolioRequest;
import com.fpt.producerworkbench.dto.request.PortfolioSectionRequest;
import com.fpt.producerworkbench.dto.request.PersonalProjectRequest;
import com.fpt.producerworkbench.dto.request.SocialLinkRequest;
import com.fpt.producerworkbench.dto.response.PortfolioResponse;
import com.fpt.producerworkbench.entity.*;
import com.fpt.producerworkbench.exception.AppException;
import com.fpt.producerworkbench.exception.ErrorCode;
import com.fpt.producerworkbench.mapper.PortfolioMapper;
import com.fpt.producerworkbench.repository.GenreRepository;
import com.fpt.producerworkbench.repository.PortfolioRepository;
import com.fpt.producerworkbench.repository.TagRepository;
import com.fpt.producerworkbench.repository.UserRepository;
import com.fpt.producerworkbench.service.FileKeyGenerator;
import com.fpt.producerworkbench.service.FileStorageService;
import com.fpt.producerworkbench.service.PortfolioService;
import com.fpt.producerworkbench.service.PublicUrlService;
import com.fpt.producerworkbench.utils.SecurityUtils;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class PortfolioServiceImpl implements PortfolioService {

    PortfolioRepository portfolioRepository;
    GenreRepository genreRepository;
    TagRepository tagRepository;
    UserRepository userRepository;
    PortfolioMapper portfolioMapper;
    FileStorageService fileStorageService;
    FileKeyGenerator fileKeyGenerator;
    PublicUrlService publicUrlService;

    @Override
    @Transactional
    public PortfolioResponse create(PortfolioRequest request, MultipartFile coverImage) {
        log.info("Creating portfolio for current user");

        String email = SecurityUtils.getCurrentUserLogin()
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHENTICATED));

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        if (portfolioRepository.existsByUserId(user.getId())) {
            throw new AppException(ErrorCode.PORTFOLIO_ALREADY_EXISTS);
        }

        String coverImageKey = null;
        if (coverImage != null && !coverImage.isEmpty()) {
            log.info("Uploading cover image for user ID: {}", user.getId());
            coverImageKey = fileKeyGenerator.generatePortfolioCoverImageKey(user.getId(), coverImage.getOriginalFilename());
            fileStorageService.uploadFile(coverImage, coverImageKey);
            log.info("Cover image uploaded successfully. Key: {}", coverImageKey);
        }

        Set<Genre> genres = new HashSet<>();
        if (request.getGenreIds() != null && !request.getGenreIds().isEmpty()) {
            List<Genre> foundGenres = genreRepository.findAllById(request.getGenreIds());
            if (foundGenres.size() != request.getGenreIds().size()) {
                log.warn("Some genre IDs not found. Requested: {}, Found: {}",
                        request.getGenreIds().size(), foundGenres.size());
            }
            genres.addAll(foundGenres);
        }

        Set<Tag> tags = new HashSet<>();
        if (request.getTags() != null && !request.getTags().isEmpty()) {
            for (String tagName : request.getTags()) {
                Tag tag = tagRepository.findByName(tagName)
                        .orElseGet(() -> {
                            log.info("Creating new tag: {}", tagName);
                            return tagRepository.save(Tag.builder()
                                    .name(tagName)
                                    .build());
                        });
                tags.add(tag);
            }
        }

        Portfolio portfolio = Portfolio.builder()
                .user(user)
                .customUrlSlug(request.getCustomUrlSlug())
                .headline(request.getHeadline())
                .coverImageUrl(coverImageKey)
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .isPublic(true)
                .genres(genres)
                .tags(tags)
                .build();

        Set<PortfolioSection> sections = new HashSet<>();
        if (request.getSections() != null && !request.getSections().isEmpty()) {
            for (PortfolioSectionRequest sectionReq : request.getSections()) {
                PortfolioSection section = PortfolioSection.builder()
                        .portfolio(portfolio)
                        .title(sectionReq.getTitle())
                        .content(sectionReq.getContent())
                        .displayOrder(sectionReq.getDisplayOrder())
                        .sectionType(sectionReq.getSectionType())
                        .build();
                sections.add(section);
            }
        }
        portfolio.setSections(sections);

        Set<PersonalProject> personalProjects = new HashSet<>();
        if (request.getPersonalProjects() != null && !request.getPersonalProjects().isEmpty()) {
            for (PersonalProjectRequest projectReq : request.getPersonalProjects()) {
                PersonalProject project = PersonalProject.builder()
                        .portfolio(portfolio)
                        .title(projectReq.getTitle())
                        .description(projectReq.getDescription())
                        .audioDemoUrl(projectReq.getAudioDemoUrl())
                        .coverImageUrl(projectReq.getCoverImageUrl())
                        .releaseYear(projectReq.getReleaseYear())
                        .build();
                personalProjects.add(project);
            }
        }
        portfolio.setPersonalProjects(personalProjects);

        Set<SocialLink> socialLinks = new HashSet<>();
        if (request.getSocialLinks() != null && !request.getSocialLinks().isEmpty()) {
            for (SocialLinkRequest linkReq : request.getSocialLinks()) {
                SocialLink link = SocialLink.builder()
                        .portfolio(portfolio)
                        .platform(linkReq.getPlatform())
                        .url(linkReq.getUrl())
                        .build();
                socialLinks.add(link);
            }
        }
        portfolio.setSocialLinks(socialLinks);

        Portfolio savedPortfolio = portfolioRepository.save(portfolio);
        log.info("Portfolio created successfully for user ID: {}", user.getId());

        PortfolioResponse response = portfolioMapper.toPortfolioResponse(savedPortfolio);

        // Convert S3 keys to presigned URLs
        convertS3KeysToUrls(response);

        return response;
    }

    /**
     * Convert all S3 keys in PortfolioResponse to URLs.
     *
     * Uses PublicUrlService which automatically chooses:
     * - Public CloudFront URLs (no expiration) for portfolio/avatar images when enabled
     * - Presigned URLs (24h expiration) as fallback or when public URLs disabled
     *
     * Configuration in application.yml:
     * cloudfront:
     *   domain: d123abc.cloudfront.net
     *   use-public-urls: true  # Enable permanent URLs for portfolio/avatar
     */
    private void convertS3KeysToUrls(PortfolioResponse response) {
        // Convert cover image key to URL (public CloudFront URL if enabled)
        if (response.getCoverImageUrl() != null && !response.getCoverImageUrl().isEmpty()) {
            String url = publicUrlService.toUrl(response.getCoverImageUrl());
            response.setCoverImageUrl(url);
            log.debug("Converted cover image key to URL: {}", url);
        }

        // Convert avatar URL if it's an S3 key (public CloudFront URL if enabled)
        if (response.getAvatarUrl() != null && !response.getAvatarUrl().isEmpty()
                && !response.getAvatarUrl().startsWith("http")) {
            String url = publicUrlService.toUrl(response.getAvatarUrl());
            response.setAvatarUrl(url);
            log.debug("Converted avatar key to URL: {}", url);
        }

        // Convert personal project cover images (public CloudFront URL if enabled)
        if (response.getPersonalProjects() != null) {
            response.getPersonalProjects().forEach(project -> {
                if (project.getCoverImageUrl() != null && !project.getCoverImageUrl().isEmpty()
                        && !project.getCoverImageUrl().startsWith("http")) {
                    String url = publicUrlService.toUrl(project.getCoverImageUrl());
                    project.setCoverImageUrl(url);
                    log.debug("Converted project cover image key to URL: {}", url);
                }
            });
        }
    }

    @Override
    public PortfolioResponse findById(Long id) {
        return null;
    }

    @Override
    public PortfolioResponse update(Long id, PortfolioRequest request) {
        return null;
    }
}
