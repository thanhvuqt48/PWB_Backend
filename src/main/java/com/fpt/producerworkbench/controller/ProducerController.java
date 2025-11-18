package com.fpt.producerworkbench.controller;

import com.fpt.producerworkbench.dto.request.RecommendationRequest;
import com.fpt.producerworkbench.dto.response.ApiResponse;
import com.fpt.producerworkbench.dto.response.ProducerSummaryResponse;
import com.fpt.producerworkbench.dto.response.SpotifyLinkInfoResponse;
import com.fpt.producerworkbench.dto.response.SpotifyRecommendationResponse;
import com.fpt.producerworkbench.service.ProducerService;
import com.fpt.producerworkbench.service.SpotifyService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
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
    public ResponseEntity<ApiResponse<SpotifyRecommendationResponse>> recommendProducersBySpotify(
            @RequestBody RecommendationRequest request, Pageable pageable) {

        // Lấy thông tin về link Spotify (type và genres)
        SpotifyLinkInfoResponse linkInfo = spotifyService.getSpotifyLinkInfo(request.getLink());
        
        // Lấy danh sách producers được đề xuất
        Page<ProducerSummaryResponse> results = producerService.recommendBySpotifyTrack(request.getLink(), pageable);

        // Tạo response kết hợp
        SpotifyRecommendationResponse recommendationResponse = SpotifyRecommendationResponse.builder()
                .linkInfo(linkInfo)
                .producers(results)
                .build();

        ApiResponse<SpotifyRecommendationResponse> apiResponse = ApiResponse.<SpotifyRecommendationResponse>builder()
                .code(200)
                .message("Đã lấy được đề xuất của nhà sản xuất thành công.")
                .result(recommendationResponse)
                .build();

        return ResponseEntity.ok(apiResponse);
    }

}
