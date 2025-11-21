package com.fpt.producerworkbench.service.impl;

import com.fpt.producerworkbench.common.PortfolioSectionType;
import com.fpt.producerworkbench.common.SocialPlatform;
import com.fpt.producerworkbench.dto.request.*;
import com.fpt.producerworkbench.dto.response.PortfolioResponse;
import com.fpt.producerworkbench.entity.*;
import com.fpt.producerworkbench.exception.AppException;
import com.fpt.producerworkbench.exception.ErrorCode;
import com.fpt.producerworkbench.mapper.PortfolioMapper;
import com.fpt.producerworkbench.repository.*;
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
    PortfolioSectionRepository portfolioSectionRepository;
    PersonalProjectRepository personalProjectRepository;
    SocialLinkRepository socialLinkRepository;
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
        String coverImageUrl = null;
        if (coverImage != null && !coverImage.isEmpty()) {
            log.info("Uploading cover image for user ID: {}", user.getId());
            coverImageKey = fileKeyGenerator.generatePortfolioCoverImageKey(user.getId(),
                    coverImage.getOriginalFilename());
            fileStorageService.uploadFile(coverImage, coverImageKey);
            coverImageUrl = fileStorageService.generatePermanentUrl(coverImageKey);
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
                .coverImageUrl(coverImageUrl)
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .isPublic(true)
                .genres(genres)
                .tags(tags)
                .build();

        Set<PortfolioSection> sections = new HashSet<>();
        if (request.getSections() != null && !request.getSections().isEmpty()) {
            Set<PortfolioSectionType> sectionTypes = new HashSet<>();
            for (PortfolioSectionRequest sectionReq : request.getSections()) {
                if (!sectionTypes.add(sectionReq.getSectionType())) {
                    throw new AppException(ErrorCode.DUPLICATE_SECTION_TYPE);
                }
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
            Set<SocialPlatform> platforms = new HashSet<>();
            for (SocialLinkRequest linkReq : request.getSocialLinks()) {
                if (!platforms.add(linkReq.getPlatform())) {
                    throw new AppException(ErrorCode.DUPLICATE_SOCIAL_PLATFORM);
                }
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

        return response;
    }

    @Override
    public PortfolioResponse findById(Long id) {
        log.info("Finding portfolio by ID: {}", id);

        Portfolio portfolio = portfolioRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.PORTFOLIO_NOT_FOUND));

        PortfolioResponse response = portfolioMapper.toPortfolioResponse(portfolio);

        log.info("Portfolio found successfully. ID: {}", id);
        return response;
    }

    @Override
    @Transactional
    public PortfolioResponse updatePersonalPortfolio(PortfolioUpdateRequest request, MultipartFile coverImage) {
        String email = SecurityUtils.getCurrentUserLogin()
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHENTICATED));

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        Portfolio portfolio = portfolioRepository.findByUserId(user.getId())
                .orElseThrow(() -> new AppException(ErrorCode.PORTFOLIO_NOT_FOUND));

        if (coverImage != null && !coverImage.isEmpty()) {
            log.info("Updating cover image for portfolio ID: {}", portfolio.getId());

            // Xóa ảnh cũ nếu có
            if (portfolio.getCoverImageUrl() != null && !portfolio.getCoverImageUrl().isEmpty()) {
                try {
                    fileStorageService.deleteFile(portfolio.getCoverImageUrl());
                    log.info("Deleted old cover image: {}", portfolio.getCoverImageUrl());
                } catch (Exception e) {
                    log.warn("Failed to delete old cover image: {}", e.getMessage());
                }
            }

            String coverImageKey = fileKeyGenerator.generatePortfolioCoverImageKey(user.getId(),
                    coverImage.getOriginalFilename());
            fileStorageService.uploadFile(coverImage, coverImageKey);
            String coverImageUrl = fileStorageService.generatePermanentUrl(coverImageKey);
            portfolio.setCoverImageUrl(coverImageUrl);
            log.info("Uploaded new cover image: {}", coverImageKey);
        }

        if (request.getCustomUrlSlug() != null) {
            portfolio.setCustomUrlSlug(request.getCustomUrlSlug());
        }
        if (request.getHeadline() != null) {
            portfolio.setHeadline(request.getHeadline());
        }
        if (request.getLatitude() != null) {
            portfolio.setLatitude(request.getLatitude());
        }
        if (request.getLongitude() != null) {
            portfolio.setLongitude(request.getLongitude());
        }

        if (request.getGenreIds() != null) {
            Set<Genre> genres = new HashSet<>();
            if (!request.getGenreIds().isEmpty()) {
                List<Genre> foundGenres = genreRepository.findAllById(request.getGenreIds());
                if (foundGenres.size() != request.getGenreIds().size()) {
                    log.warn("Some genre IDs not found. Requested: {}, Found: {}",
                            request.getGenreIds().size(), foundGenres.size());
                }
                genres.addAll(foundGenres);
            }
            portfolio.setGenres(genres);
        }

        if (request.getTags() != null) {
            Set<Tag> tags = new HashSet<>();
            if (!request.getTags().isEmpty()) {
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
            portfolio.setTags(tags);
        }

        if (request.getSections() != null) {
            syncPortfolioSections(portfolio, request.getSections());
        }

        if (request.getPersonalProjects() != null) {

            for (PersonalProjectUpdateRequest projectReq : request.getPersonalProjects()) {
                if (projectReq.getId() != null) {
                    PersonalProject existingProject = personalProjectRepository.findById(projectReq.getId())
                            .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND));

                    if (!existingProject.getPortfolio().getId().equals(portfolio.getId())) {
                        throw new AppException(ErrorCode.UNAUTHORIZED);
                    }

                    existingProject.setTitle(projectReq.getTitle());
                    existingProject.setDescription(projectReq.getDescription());
                    existingProject.setAudioDemoUrl(projectReq.getAudioDemoUrl());
                    existingProject.setCoverImageUrl(projectReq.getCoverImageUrl());
                    existingProject.setReleaseYear(projectReq.getReleaseYear());
                    personalProjectRepository.save(existingProject);
                    log.debug("Updated personal project ID: {}", projectReq.getId());
                } else {
                    PersonalProject newProject = PersonalProject.builder()
                            .portfolio(portfolio)
                            .title(projectReq.getTitle())
                            .description(projectReq.getDescription())
                            .audioDemoUrl(projectReq.getAudioDemoUrl())
                            .coverImageUrl(projectReq.getCoverImageUrl())
                            .releaseYear(projectReq.getReleaseYear())
                            .build();
                    portfolio.getPersonalProjects().add(newProject);
                    personalProjectRepository.save(newProject);
                    log.debug("Created new personal project: {}", projectReq.getTitle());
                }
            }
        }

        if (request.getSocialLinks() != null) {
            syncSocialLinks(portfolio, request.getSocialLinks());
        }

        Portfolio updatedPortfolio = portfolioRepository.save(portfolio);

        PortfolioResponse response = portfolioMapper.toPortfolioResponse(updatedPortfolio);

        return response;
    }

    @Override
    public PortfolioResponse getPersonalPortfolio() {
        String email = SecurityUtils.getCurrentUserLogin()
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHENTICATED));

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        Portfolio portfolio = portfolioRepository.findByUserId(user.getId())
                .orElseThrow(() -> new AppException(ErrorCode.PORTFOLIO_NOT_FOUND));

        PortfolioResponse response = portfolioMapper.toPortfolioResponse(portfolio);

        log.info("Portfolio found successfully for user email: {}", user.getEmail());
        return response;
    }

    @Override
    public PortfolioResponse getPortfolioByUserId(Long userId) {
        log.info("Finding portfolio by user ID: {}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        Portfolio portfolio = portfolioRepository.findByUserId(userId)
                .orElseThrow(() -> new AppException(ErrorCode.PORTFOLIO_NOT_FOUND));

        PortfolioResponse response = portfolioMapper.toPortfolioResponse(portfolio);

        log.info("Portfolio found successfully for user ID: {}", userId);
        return response;
    }

    @Override
    public PortfolioResponse getPortfolioByCustomUrlSlug(String slug) {
        log.info("Finding portfolio by custom URL slug: {}", slug);

        Portfolio portfolio = portfolioRepository.findByCustomUrlSlug(slug)
                .orElseThrow(() -> new AppException(ErrorCode.PORTFOLIO_NOT_FOUND));

        PortfolioResponse response = portfolioMapper.toPortfolioResponse(portfolio);

        log.info("Portfolio found successfully for custom URL slug: {}", slug);
        return response;
    }

    private void syncPortfolioSections(
            Portfolio portfolio,
            List<PortfolioSectionUpdateRequest> sectionRequests) {

        Set<PortfolioSectionType> types = new HashSet<>();
        for (PortfolioSectionUpdateRequest req : sectionRequests) {
            if (!types.add(req.getSectionType())) {
                throw new AppException(ErrorCode.DUPLICATE_SECTION_TYPE);
            }
        }

        List<PortfolioSection> existing = portfolioSectionRepository
                .findAllByPortfolioId(portfolio.getId());

        if (!existing.isEmpty()) {
            portfolioSectionRepository.deleteAll(existing);
        }
        portfolio.getSections().clear();

        List<PortfolioSection> newSections = sectionRequests.stream()
                .map(req -> PortfolioSection.builder()
                        .portfolio(portfolio)
                        .title(req.getTitle())
                        .content(req.getContent())
                        .displayOrder(req.getDisplayOrder())
                        .sectionType(req.getSectionType())
                        .build())
                .toList();

        portfolioSectionRepository.saveAll(newSections);
        portfolio.getSections().addAll(newSections);
    }

    private void syncSocialLinks(Portfolio portfolio,
            List<SocialLinkUpdateRequest> socialLinkRequests) {

        Set<SocialPlatform> platforms = new HashSet<>();
        for (SocialLinkUpdateRequest linkReq : socialLinkRequests) {
            if (!platforms.add(linkReq.getPlatform())) {
                throw new AppException(ErrorCode.DUPLICATE_SOCIAL_PLATFORM);
            }
        }

        List<SocialLink> existingLinks = socialLinkRepository.findAllByPortfolioId(portfolio.getId());
        socialLinkRepository.deleteAll(existingLinks);
        portfolio.getSocialLinks().clear();

        List<SocialLink> newLinks = socialLinkRequests.stream()
                .map(req -> SocialLink.builder()
                        .portfolio(portfolio)
                        .platform(req.getPlatform())
                        .url(req.getUrl())
                        .build())
                .toList();

        socialLinkRepository.saveAll(newLinks);
        portfolio.getSocialLinks().addAll(newLinks);
    }

}
