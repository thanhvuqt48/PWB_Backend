package com.fpt.producerworkbench.controller;

import com.fpt.producerworkbench.dto.request.RecommendationRequest;
import com.fpt.producerworkbench.dto.response.ApiResponse;
import com.fpt.producerworkbench.dto.response.ProducerSummaryResponse;
import com.fpt.producerworkbench.service.ProducerService;
import com.fpt.producerworkbench.service.SpotifyService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/producers")
@RequiredArgsConstructor
public class ProducerController {

    private final ProducerService producerService;
    private final SpotifyService spotifyService;

    @GetMapping
    public ApiResponse<Page<ProducerSummaryResponse>> getProducers(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) List<Integer> genreIds,
            @RequestParam(required = false) List<String> tags,
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lon,
            @RequestParam(required = false) Double radius,
            Pageable pageable) {

        Page<ProducerSummaryResponse> result = producerService.searchProducers(
                search, genreIds, tags, lat, lon, radius, pageable);

        return ApiResponse.<Page<ProducerSummaryResponse>>builder()
                .result(result)
                .build();
    }

    @PostMapping("/recommend-by-spotify")
    public ApiResponse<Page<ProducerSummaryResponse>> recommendBySpotifyLink(
            @RequestBody RecommendationRequest request) {

        List<String> genres = spotifyService.getGenresFromTrackLink(request.getLink());

        if (genres.isEmpty()) {
            return ApiResponse.<Page<ProducerSummaryResponse>>builder()
                    .code(404)
                    .message("Could not find genres for the provided link.")
                    .build();
        }

        Pageable pageable = PageRequest.of(0, 5);
        Page<ProducerSummaryResponse> recommendedProducers = producerService.searchProducers(
                null, null, genres, null, null, null, pageable
        );

        return ApiResponse.<Page<ProducerSummaryResponse>>builder()
                .result(recommendedProducers)
                .build();
    }
}
