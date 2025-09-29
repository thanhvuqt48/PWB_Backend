package com.fpt.producerworkbench.controller;


import com.fpt.producerworkbench.dto.request.SubscriptionPlanRequest;
import com.fpt.producerworkbench.dto.response.ApiResponse;
import com.fpt.producerworkbench.dto.response.SubscriptionPlanResponse;
import com.fpt.producerworkbench.service.SubscriptionPlanService;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RequestMapping("/api/v1/subscription-plans")
@Slf4j
public class SubscriptionPlanController {

    SubscriptionPlanService subscriptionPlanService;

    @PostMapping
    ApiResponse<SubscriptionPlanResponse> create(@Valid @RequestBody SubscriptionPlanRequest request) {
        log.info("Creating subscription plan: {}", request.getName());

        var result = subscriptionPlanService.create(request);

        return ApiResponse.<SubscriptionPlanResponse>builder()
                .code(HttpStatus.CREATED.value())
                .message("Tạo gói đăng ký thành công")
                .result(result)
                .build();
    }

    @GetMapping("/{id}")
    ApiResponse<SubscriptionPlanResponse> findById(@PathVariable Long id) {
        log.info("Finding subscription plan by ID: {}", id);

        var result = subscriptionPlanService.findById(id);

        return ApiResponse.<SubscriptionPlanResponse>builder()
                .code(HttpStatus.OK.value())
                .result(result)
                .build();
    }

    @GetMapping
    ApiResponse<Page<SubscriptionPlanResponse>> findAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {

        log.info("Finding all subscription plans - page: {}, size: {}", page, size);

        Sort sort = sortDir.equalsIgnoreCase("desc") ?
                Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();
        Pageable pageable = PageRequest.of(page, size, sort);

        var result = subscriptionPlanService.findAll(pageable);

        return ApiResponse.<Page<SubscriptionPlanResponse>>builder()
                .code(HttpStatus.OK.value())
                .result(result)
                .build();
    }

    @GetMapping("/search")
    ApiResponse<Page<SubscriptionPlanResponse>> search(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String currency,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {

        log.info("Searching subscription plans with filters");

        Sort sort = sortDir.equalsIgnoreCase("desc") ?
                Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();
        Pageable pageable = PageRequest.of(page, size, sort);

        var result = subscriptionPlanService.findWithFilters(name, currency, minPrice, maxPrice, pageable);

        return ApiResponse.<Page<SubscriptionPlanResponse>>builder()
                .code(HttpStatus.OK.value())
                .result(result)
                .build();
    }

    @GetMapping("/currency/{currency}")
    ApiResponse<List<SubscriptionPlanResponse>> findByCurrency(@PathVariable String currency) {
        log.info("Finding subscription plans by currency: {}", currency);

        var result = subscriptionPlanService.findByCurrency(currency);

        return ApiResponse.<List<SubscriptionPlanResponse>>builder()
                .code(HttpStatus.OK.value())
                .result(result)
                .build();
    }

    @PutMapping("/{id}")
    ApiResponse<SubscriptionPlanResponse> update(@PathVariable Long id,
                                                 @Valid @RequestBody SubscriptionPlanRequest request) {
        log.info("Updating subscription plan with ID: {}", id);

        var result = subscriptionPlanService.update(id, request);

        return ApiResponse.<SubscriptionPlanResponse>builder()
                .code(HttpStatus.OK.value())
                .message("Cập nhật gói đăng ký thành công")
                .result(result)
                .build();
    }

    @DeleteMapping("/{id}")
    ApiResponse<Void> delete(@PathVariable Long id) {
        log.info("Deleting subscription plan with ID: {}", id);

        subscriptionPlanService.delete(id);

        return ApiResponse.<Void>builder()
                .code(HttpStatus.OK.value())
                .message("Xóa gói đăng ký thành công")
                .build();
    }
}
