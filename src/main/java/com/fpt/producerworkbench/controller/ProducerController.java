package com.fpt.producerworkbench.controller;

import com.fpt.producerworkbench.dto.response.ApiResponse;
import com.fpt.producerworkbench.dto.response.ProducerSummaryResponse;
import com.fpt.producerworkbench.service.ProducerService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/producers")
@RequiredArgsConstructor
public class ProducerController {

    private final ProducerService producerService;

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
}
