package com.fpt.producerworkbench.service.impl;

import com.fpt.producerworkbench.service.SubscriptionPlanService;
import com.fpt.producerworkbench.dto.request.SubscriptionPlanRequest;
import com.fpt.producerworkbench.dto.response.SubscriptionPlanResponse;
import com.fpt.producerworkbench.entity.SubscriptionPlan;
import com.fpt.producerworkbench.exception.AppException;
import com.fpt.producerworkbench.exception.ErrorCode;
import com.fpt.producerworkbench.mapper.SubscriptionPlanMapper;
import com.fpt.producerworkbench.repository.SubscriptionPlanRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;


@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class SubscriptionPlanServiceImpl implements SubscriptionPlanService {

    SubscriptionPlanRepository subscriptionPlanRepository;
    SubscriptionPlanMapper subscriptionPlanMapper;

    @Override
    @Transactional
    public SubscriptionPlanResponse create(SubscriptionPlanRequest request) {
        log.info("Creating subscription plan with name: {}", request.getName());

        // Check if name already exists
        if (subscriptionPlanRepository.existsByName(request.getName())) {
            log.warn("Subscription plan with name {} already exists", request.getName());
            throw new AppException(ErrorCode.DUPLICATE_RESOURCE);
        }

        // Convert DTO to Entity
        SubscriptionPlan subscriptionPlan = subscriptionPlanMapper.toEntity(request);

        // Save to database
        SubscriptionPlan savedPlan = subscriptionPlanRepository.save(subscriptionPlan);

        log.info("Successfully created subscription plan with ID: {}", savedPlan.getId());
        return subscriptionPlanMapper.toResponse(savedPlan);
    }

    @Override
    @Transactional(readOnly = true)
    public SubscriptionPlanResponse findById(Long id) {
        log.info("Finding subscription plan by ID: {}", id);

        SubscriptionPlan subscriptionPlan = subscriptionPlanRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Subscription plan not found with ID: {}", id);
                    return new AppException(ErrorCode.RESOURCE_NOT_FOUND);
                });

        return subscriptionPlanMapper.toResponse(subscriptionPlan);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<SubscriptionPlanResponse> findAll(Pageable pageable) {
        log.info("Finding all subscription plans with pagination: {}", pageable);

        Page<SubscriptionPlan> subscriptionPlans = subscriptionPlanRepository.findAll(pageable);
        return subscriptionPlans.map(subscriptionPlanMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<SubscriptionPlanResponse> findWithFilters(String name, String currency,
                                                          BigDecimal minPrice, BigDecimal maxPrice,
                                                          Pageable pageable) {
        log.info("Finding subscription plans with filters - name: {}, currency: {}, price range: {}-{}",
                name, currency, minPrice, maxPrice);

        Page<SubscriptionPlan> subscriptionPlans = subscriptionPlanRepository
                .findWithFilters(name, currency, minPrice, maxPrice, pageable);

        return subscriptionPlans.map(subscriptionPlanMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SubscriptionPlanResponse> findByCurrency(String currency) {
        log.info("Finding subscription plans by currency: {}", currency);

        List<SubscriptionPlan> subscriptionPlans = subscriptionPlanRepository.findByCurrency(currency);
        return subscriptionPlans.stream()
                .map(subscriptionPlanMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public SubscriptionPlanResponse update(Long id, SubscriptionPlanRequest request) {
        log.info("Updating subscription plan with ID: {}", id);

        // Find existing plan
        SubscriptionPlan existingPlan = subscriptionPlanRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Subscription plan not found with ID: {}", id);
                    return new AppException(ErrorCode.RESOURCE_NOT_FOUND);
                });

        // Check if name already exists for other plans
        if (subscriptionPlanRepository.existsByNameAndIdNot(request.getName(), id)) {
            log.warn("Subscription plan with name {} already exists", request.getName());
            throw new AppException(ErrorCode.DUPLICATE_RESOURCE);
        }

        // Update fields
        subscriptionPlanMapper.updateEntityFromRequest(request, existingPlan);

        // Save updated plan
        SubscriptionPlan updatedPlan = subscriptionPlanRepository.save(existingPlan);

        log.info("Successfully updated subscription plan with ID: {}", id);
        return subscriptionPlanMapper.toResponse(updatedPlan);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        log.info("Deleting subscription plan with ID: {}", id);

        if (!subscriptionPlanRepository.existsById(id)) {
            log.warn("Subscription plan not found with ID: {}", id);
            throw new AppException(ErrorCode.RESOURCE_NOT_FOUND);
        }

        subscriptionPlanRepository.deleteById(id);
        log.info("Successfully deleted subscription plan with ID: {}", id);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByName(String name) {
        return subscriptionPlanRepository.existsByName(name);
    }
}

