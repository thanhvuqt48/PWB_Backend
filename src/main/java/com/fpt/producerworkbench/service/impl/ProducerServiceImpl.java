package com.fpt.producerworkbench.service.impl;

import com.fpt.producerworkbench.dto.response.PortfolioWithDistanceResponse;
import com.fpt.producerworkbench.dto.response.ProducerSummaryResponse;
import com.fpt.producerworkbench.entity.Portfolio;
import com.fpt.producerworkbench.mapper.PortfolioMapper;
import com.fpt.producerworkbench.repository.PortfolioRepository;
import com.fpt.producerworkbench.repository.ProducerSpecification;
import com.fpt.producerworkbench.service.ProducerService;
import com.fpt.producerworkbench.service.SpotifyService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import com.fpt.producerworkbench.entity.Genre;
import com.fpt.producerworkbench.repository.GenreRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProducerServiceImpl implements ProducerService {

    private final PortfolioRepository portfolioRepository;
    private final PortfolioMapper portfolioMapper;
    private final GenreRepository genreRepository;
    private final SpotifyService spotifyService;

    @Override
    public Page<ProducerSummaryResponse> searchProducers(
            String name, List<Integer> genreIds, List<String> tags,
            Double lat, Double lon, Double radius,
            Pageable pageable) {

        List<String> remainingTags = new ArrayList<>();
        List<Integer> finalGenreIds = new ArrayList<>();

        if (genreIds != null) {
            finalGenreIds.addAll(genreIds);
        }

        if (tags != null && !tags.isEmpty()) {
            List<Genre> foundGenres = genreRepository.findByNameIn(tags);

            Set<Integer> foundGenreIds = foundGenres.stream()
                    .map(Genre::getId)
                    .collect(Collectors.toSet());
            finalGenreIds.addAll(foundGenreIds);

            Set<String> foundGenreNames = foundGenres.stream()
                    .map(Genre::getName)
                    .collect(Collectors.toSet());

            remainingTags = tags.stream()
                    .filter(tag -> !foundGenreNames.contains(tag))
                    .collect(Collectors.toList());
        }

        // Luôn filter chỉ lấy portfolio của producer
        Specification<Portfolio> spec = ProducerSpecification.isProducer();

        if (StringUtils.hasText(name)) {
            spec = spec.and(ProducerSpecification.hasName(name));
        }
        if (!finalGenreIds.isEmpty()) {
            spec = spec.and(ProducerSpecification.hasGenres(finalGenreIds));
        }
        if (!remainingTags.isEmpty()) {
            spec = spec.and(ProducerSpecification.hasTags(remainingTags));
        }

        // Áp dụng filter theo bán kính nếu có lat, lon và radius
        if (lat != null && lon != null && radius != null && radius > 0) {
            spec = spec.and(ProducerSpecification.isWithinRadius(lat, lon, radius));
        }

        if (lat == null || lon == null) {
            Page<Portfolio> portfolioPage = portfolioRepository.findAll(spec, pageable);
            return portfolioPage.map(p -> portfolioMapper.toProducerSummaryResponse(p, null));
        }

        Page<PortfolioWithDistanceResponse> resultPage = portfolioRepository.findWithDistance(spec, lat, lon, pageable);

        return resultPage.map(p -> portfolioMapper.toProducerSummaryResponse(p.getPortfolio(), p.getDistanceInKm()));
    }

    @Override
    public Page<ProducerSummaryResponse> recommendBySpotifyTrack(String trackLink, Pageable pageable) {
        List<String> musicStylesFromSpotify = spotifyService.getGenresFromTrackLink(trackLink);

        if (musicStylesFromSpotify == null || musicStylesFromSpotify.isEmpty()) {
            return Page.empty(pageable);
        }

        // Luôn filter chỉ lấy portfolio của producer
        Specification<Portfolio> spec = ProducerSpecification.isProducer()
                .and(ProducerSpecification.hasGenresOrTags(musicStylesFromSpotify));

        Page<Portfolio> portfolioPage = portfolioRepository.findAll(spec, pageable);

        return portfolioPage.map(portfolio -> portfolioMapper.toProducerSummaryResponse(portfolio, null));
    }

}
