package com.fpt.producerworkbench.service;

import com.fpt.producerworkbench.dto.response.ProducerSummaryResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface ProducerService {
    Page<ProducerSummaryResponse> searchProducers(
            String name, List<Integer> genreIds, List<String> tags,
            Double lat, Double lon, Double radius,
            Pageable pageable);
}