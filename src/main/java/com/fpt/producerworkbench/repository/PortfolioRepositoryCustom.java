package com.fpt.producerworkbench.repository;

import com.fpt.producerworkbench.dto.response.PortfolioWithDistanceResponse;
import com.fpt.producerworkbench.entity.Portfolio;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

public interface PortfolioRepositoryCustom {
    Page<PortfolioWithDistanceResponse> findWithDistance(
            Specification<Portfolio> spec,
            Double lat, Double lon,
            Pageable pageable);
}
