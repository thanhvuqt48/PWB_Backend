package com.fpt.producerworkbench.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fpt.producerworkbench.dto.request.PortfolioRequest;
import com.fpt.producerworkbench.dto.request.PortfolioUpdateRequest;
import com.fpt.producerworkbench.dto.response.ApiResponse;
import com.fpt.producerworkbench.dto.response.PortfolioResponse;
import com.fpt.producerworkbench.service.PortfolioService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Set;

@RestController
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RequestMapping("/api/v1/portfolios")
@Slf4j
public class PortfolioController {

    PortfolioService portfolioService;
    ObjectMapper objectMapper;
    Validator validator;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<PortfolioResponse> create(
            @RequestPart(value = "coverImage", required = false) MultipartFile coverImage,
            @RequestPart("data") String dataJson) {

        log.info("Creating new portfolio");

        try {
            PortfolioRequest request = objectMapper.readValue(dataJson, PortfolioRequest.class);

            validateRequest(request);

            PortfolioResponse result = portfolioService.create(request, coverImage);

            log.info("Portfolio created successfully with ID: {}", result.getId());

            return ApiResponse.<PortfolioResponse>builder()
                    .message("Tạo portfolio thành công")
                    .code(HttpStatus.CREATED.value())
                    .result(result)
                    .build();
        } catch (Exception e) {
            log.error("Error parsing portfolio request JSON", e);
            throw new RuntimeException("Lỗi parse JSON request: " + e.getMessage(), e);
        }
    }


    @GetMapping("/{id}")
    public ApiResponse<PortfolioResponse> getById(@PathVariable Long id) {
        log.info("Getting portfolio by ID: {}", id);

        PortfolioResponse result = portfolioService.findById(id);

        return ApiResponse.<PortfolioResponse>builder()
                .message("Lấy thông tin portfolio thành công")
                .code(HttpStatus.OK.value())
                .result(result)
                .build();
    }

    @PutMapping(value = "/personal", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<PortfolioResponse> update(
            @RequestPart(value = "coverImage", required = false) MultipartFile coverImage,
            @RequestPart("data") String dataJson) {

        log.info("Updating personal portfolio");

        try {
            PortfolioUpdateRequest request = objectMapper.readValue(dataJson, PortfolioUpdateRequest.class);

            // Validate request
            validateRequest(request);

            PortfolioResponse result = portfolioService.updatePersonalPortfolio(request, coverImage);

            log.info("Portfolio updated successfully");

            return ApiResponse.<PortfolioResponse>builder()
                    .message("Cập nhật portfolio thành công")
                    .code(HttpStatus.OK.value())
                    .result(result)
                    .build();
        } catch (Exception e) {
            log.error("Error parsing portfolio update request JSON", e);
            throw new RuntimeException("Lỗi parse JSON request: " + e.getMessage(), e);
        }
    }

    @GetMapping("/personal")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<PortfolioResponse> getPersonalPortfolio() {

        PortfolioResponse result = portfolioService.getPersonalPortfolio();

        return ApiResponse.<PortfolioResponse>builder()
                .message("Lấy portfolio của user thành công")
                .code(HttpStatus.OK.value())
                .result(result)
                .build();
    }

    @GetMapping("/user/{userId}")
    public ApiResponse<PortfolioResponse> getPortfolioByUserId(@PathVariable Long userId) {
        log.info("Getting portfolio for user ID: {}", userId);

        PortfolioResponse result = portfolioService.getPortfolioByUserId(userId);

        return ApiResponse.<PortfolioResponse>builder()
                .message("Lấy portfolio thành công")
                .code(HttpStatus.OK.value())
                .result(result)
                .build();
    }

    private <T> void validateRequest(T request) {
        Set<ConstraintViolation<T>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            log.error("Validation failed: {} violations found", violations.size());
            throw new jakarta.validation.ConstraintViolationException(violations);
        }
    }

}
