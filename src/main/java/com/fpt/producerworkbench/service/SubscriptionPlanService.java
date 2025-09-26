package com.fpt.producerworkbench.service;

import com.fpt.producerworkbench.dto.request.SubscriptionPlanRequest;
import com.fpt.producerworkbench.dto.response.SubscriptionPlanResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;

public interface SubscriptionPlanService {
    SubscriptionPlanResponse create(SubscriptionPlanRequest request);

    SubscriptionPlanResponse findById(Long id);

    Page<SubscriptionPlanResponse> findAll(Pageable pageable);

    Page<SubscriptionPlanResponse> findWithFilters(String name, String currency,
                                                   BigDecimal minPrice, BigDecimal maxPrice,
                                                   Pageable pageable);

    List<SubscriptionPlanResponse> findByCurrency(String currency);

    SubscriptionPlanResponse update(Long id, SubscriptionPlanRequest request);

    void delete(Long id);

    boolean existsByName(String name);
}
