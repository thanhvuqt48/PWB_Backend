package com.fpt.producerworkbench.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fpt.producerworkbench.dto.request.PortfolioRequest;
import com.fpt.producerworkbench.dto.response.ApiResponse;
import com.fpt.producerworkbench.dto.response.PortfolioResponse;
import com.fpt.producerworkbench.service.PortfolioService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RequestMapping("/api/v1/portfolios")
public class PortfolioController {

    PortfolioService portfolioService;
    ObjectMapper objectMapper;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<PortfolioResponse> create(
            @RequestPart(value = "coverImage", required = false) MultipartFile coverImage,
            @RequestPart("data") String dataJson) {

        try {
            PortfolioRequest request = objectMapper.readValue(dataJson, PortfolioRequest.class);

            PortfolioResponse result = portfolioService.create(request, coverImage);

            return ApiResponse.<PortfolioResponse>builder()
                    .message("Tạo portfolio thành công")
                    .code(HttpStatus.CREATED.value())
                    .result(result)
                    .build();
        } catch (Exception e) {
            throw new RuntimeException("Lỗi parse JSON request: " + e.getMessage(), e);
        }
    }
}
