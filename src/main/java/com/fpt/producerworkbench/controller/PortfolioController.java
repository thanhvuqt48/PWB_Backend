package com.fpt.producerworkbench.controller;

import com.fpt.producerworkbench.dto.request.PortfolioRequest;
import com.fpt.producerworkbench.dto.request.PortfolioUpdateRequest;
import com.fpt.producerworkbench.dto.response.ApiResponse;
import com.fpt.producerworkbench.dto.response.PortfolioResponse;
import com.fpt.producerworkbench.service.PortfolioService;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RequestMapping("/api/v1/portfolios")
@Slf4j
public class PortfolioController {

    PortfolioService portfolioService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<PortfolioResponse> create(
            @RequestPart(value = "coverImage", required = false) MultipartFile coverImage,
            @Valid @RequestPart("data") PortfolioRequest request) {

        PortfolioResponse result = portfolioService.create(request, coverImage);

        return ApiResponse.<PortfolioResponse>builder()
                .message("Tạo portfolio thành công")
                .code(HttpStatus.CREATED.value())
                .result(result)
                .build();
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

    @PutMapping(
            value = "/personal",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<PortfolioResponse> update(
            @RequestPart(value = "coverImage", required = false) MultipartFile coverImage,
            @Valid @RequestPart("data") PortfolioUpdateRequest request,
            MultipartHttpServletRequest multipartRequest
    ) {
        log.info("Updating personal portfolio");
        
        Map<String, MultipartFile> projectAudioDemos = new HashMap<>();
        Map<String, MultipartFile> projectCoverImages = new HashMap<>();
        
        multipartRequest.getFileMap().forEach((name, file) -> {
            if (name.startsWith("projectAudioDemos[")) {
                // Extract index from name like "projectAudioDemos[0]"
                String index = name.substring("projectAudioDemos[".length(), name.length() - 1);
                projectAudioDemos.put(index, file);
                log.debug("Found audio demo at index: {}", index);
            } else if (name.startsWith("projectCoverImages[")) {
                // Extract index from name like "projectCoverImages[0]"
                String index = name.substring("projectCoverImages[".length(), name.length() - 1);
                projectCoverImages.put(index, file);
                log.debug("Found cover image at index: {}", index);
            }
        });
        
        log.debug("Parsed projectAudioDemos: {} files", projectAudioDemos.size());
        log.debug("Parsed projectCoverImages: {} files", projectCoverImages.size());

        PortfolioResponse result = portfolioService.updatePersonalPortfolio(
                request, coverImage, projectAudioDemos, projectCoverImages);

        log.info("Portfolio updated successfully");

        return ApiResponse.<PortfolioResponse>builder()
                .message("Cập nhật portfolio thành công")
                .code(HttpStatus.OK.value())
                .result(result)
                .build();
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

    @GetMapping("/slug/{slug}")
    public ApiResponse<PortfolioResponse> getPortfolioBySlug(@PathVariable String slug) {
        log.info("Getting portfolio by slug: {}", slug);

        PortfolioResponse result = portfolioService.getPortfolioByCustomUrlSlug(slug);

        return ApiResponse.<PortfolioResponse>builder()
                .message("Lấy portfolio thành công")
                .code(HttpStatus.OK.value())
                .result(result)
                .build();
    }

}
