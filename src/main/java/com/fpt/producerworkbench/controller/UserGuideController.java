package com.fpt.producerworkbench.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fpt.producerworkbench.dto.request.UserGuideIndexRequest;
import com.fpt.producerworkbench.dto.request.UserGuideSearchRequest;
import com.fpt.producerworkbench.dto.request.UserGuideUpdateRequest;
import com.fpt.producerworkbench.dto.response.ApiResponse;
import com.fpt.producerworkbench.dto.response.IndexingResultResponse;
import com.fpt.producerworkbench.dto.response.UserGuideResponse;
import com.fpt.producerworkbench.dto.response.UserGuideSearchResponse;
import com.fpt.producerworkbench.dto.response.UserGuideStatsResponse;
import com.fpt.producerworkbench.exception.AppException;
import com.fpt.producerworkbench.exception.ErrorCode;
import com.fpt.producerworkbench.service.UserGuideIndexingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * Controller quản lý User Guides - Hướng dẫn sử dụng hệ thống
 * 
 * Admin endpoints: Index, Update, Delete guides
 * User endpoints: Search, View guides
 */
@RestController
@RequestMapping("/api/user-guides")
@RequiredArgsConstructor
@Slf4j
public class UserGuideController {

    private final UserGuideIndexingService userGuideIndexingService;
    private final ObjectMapper objectMapper;

    /**
     * [PUBLIC - Testing] Index a new user guide (with images)
     * POST /api/user-guides/index
     */
    @PostMapping(value = "/index", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<IndexingResultResponse> indexGuide(
            @RequestParam("request") String requestJson,
            @RequestPart(value = "coverImage", required = false) MultipartFile coverImage,
            @RequestPart(value = "stepImages", required = false) List<MultipartFile> stepImages) throws Exception {
        
        log.info("Received index request with JSON: {}", requestJson);
        
        // Parse JSON string to object
        UserGuideIndexRequest request = objectMapper.readValue(requestJson, UserGuideIndexRequest.class);
        
        log.info("Indexing new guide with images: {}", request.getTitle());
        
        IndexingResultResponse result = userGuideIndexingService.indexGuide(request, coverImage, stepImages);
        
        return ApiResponse.<IndexingResultResponse>builder()
                .message("Guide indexed successfully")
                .result(result)
                .build();
    }

    /**
     * [PUBLIC - Testing] Update existing guide
     * PUT /api/user-guides/{id}
     * 
     * Form fields:
     * - request: JSON string as Blob with type='application/json'
     * - coverImage: File (optional)
     * - stepImages: Multiple files (optional)
     */
    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<UserGuideResponse> updateGuide(
            @PathVariable Long id,
            @RequestParam("request") String requestJson,
            @RequestParam(value = "coverImage", required = false) MultipartFile coverImage,
            @RequestParam(value = "stepImages", required = false) List<MultipartFile> stepImages) {
        
        log.info("Updating guide ID: {} with request: {}", id, requestJson);
        
        try {
            UserGuideUpdateRequest request = objectMapper.readValue(requestJson, UserGuideUpdateRequest.class);
            UserGuideResponse result = userGuideIndexingService.updateGuide(id, request, coverImage, stepImages);
            
            return ApiResponse.<UserGuideResponse>builder()
                    .message("Guide updated successfully")
                    .result(result)
                    .build();
        } catch (JsonProcessingException e) {
            log.error("Failed to parse request JSON: {}", e.getMessage());
            throw new AppException(ErrorCode.INVALID_PARAMETER_FORMAT);
        }
    }

    /**
     * [PUBLIC - Testing] Delete guide (HARD DELETE - Permanent)
     * DELETE /api/user-guides/index/{id}
     * Completely removes guide from DB, Pinecone, and S3
     */
    @DeleteMapping("/index/{id}")
    public ApiResponse<Void> deleteGuide(@PathVariable Long id) {
        log.info("Permanently deleting guide ID: {}", id);
        userGuideIndexingService.permanentlyDeleteGuide(id); // ✅ HARD DELETE!
        
        return ApiResponse.<Void>builder()
                .message("Guide permanently deleted (DB + Pinecone + S3)")
                .build();
    }

    /**
     * [PUBLIC - Testing] Index multiple guides in batch
     * POST /api/user-guides/index/batch
     */
    @PostMapping("/index/batch")
    public ApiResponse<Map<String, Object>> indexBatch(
            @Valid @RequestBody List<UserGuideIndexRequest> requests) {
        
        log.info("Batch indexing {} guides", requests.size());
        Map<String, Object> result = userGuideIndexingService.indexMultipleGuides(requests);
        
        return ApiResponse.<Map<String, Object>>builder()
                .message("Batch indexing completed")
                .result(result)
                .build();
    }

    /**
     * [PUBLIC - Testing] Reindex all guides
     * POST /api/user-guides/reindex-all
     */
    @PostMapping("/reindex-all")
    public ApiResponse<Map<String, Object>> reindexAll() {
        log.info("Reindexing all guides");
        Map<String, Object> result = userGuideIndexingService.reindexAllGuides();
        
        return ApiResponse.<Map<String, Object>>builder()
                .message("Reindexing completed")
                .result(result)
                .build();
    }

    /**
     * [PUBLIC - Testing] Search guides by query
     * POST /api/user-guides/search
     */
    @PostMapping("/search")
    public ApiResponse<UserGuideSearchResponse> searchGuides(
            @Valid @RequestBody UserGuideSearchRequest request) {
        
        log.info("Searching guides with query: {}", request.getQuery());
        UserGuideSearchResponse result = userGuideIndexingService.searchGuides(request);
        
        return ApiResponse.<UserGuideSearchResponse>builder()
                .message("Search completed")
                .result(result)
                .build();
    }

    /**
     * [PUBLIC - Testing] Get guide by ID
     * GET /api/user-guides/{id}
     */
    @GetMapping("/{id}")
    public ApiResponse<UserGuideResponse> getGuide(@PathVariable Long id) {
        log.info("Fetching guide ID: {}", id);
        UserGuideResponse result = userGuideIndexingService.getGuideById(id);
        
        return ApiResponse.<UserGuideResponse>builder()
                .message("Guide retrieved successfully")
                .result(result)
                .build();
    }

    /**
     * [PUBLIC - Testing] Get all guides summary (lightweight, no steps/full content)
     * GET /api/user-guides
     */
    @GetMapping
    public ApiResponse<List<com.fpt.producerworkbench.dto.response.UserGuideSummaryResponse>> getAllGuides(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String difficulty,
            @RequestParam(defaultValue = "true") Boolean isActive) {
        
        log.info("Fetching all guides - category: {}, difficulty: {}, isActive: {}", 
                category, difficulty, isActive);
        
        List<com.fpt.producerworkbench.dto.response.UserGuideSummaryResponse> guides = 
                userGuideIndexingService.getAllGuides(category, difficulty, isActive);
        
        return ApiResponse.<List<com.fpt.producerworkbench.dto.response.UserGuideSummaryResponse>>builder()
                .message("Guides retrieved successfully")
                .result(guides)
                .build();
    }

    /**
     * [PUBLIC - Testing] Get all available categories
     * GET /api/user-guides/categories
     */
    @GetMapping("/categories")
    public ApiResponse<List<String>> getCategories() {
        log.info("Fetching all categories");
        List<String> categories = userGuideIndexingService.getAllCategories();
        
        return ApiResponse.<List<String>>builder()
                .message("Categories retrieved successfully")
                .result(categories)
                .build();
    }

    /**
     * [PUBLIC - Testing] Get statistics
     * GET /api/user-guides/stats
     */
    @GetMapping("/stats")
    public ApiResponse<UserGuideStatsResponse> getStats() {
        log.info("Fetching guide statistics");
        UserGuideStatsResponse stats = userGuideIndexingService.getIndexStats();
        
        return ApiResponse.<UserGuideStatsResponse>builder()
                .message("Statistics retrieved successfully")
                .result(stats)
                .build();
    }

    /**
     * Health check endpoint
     * GET /api/user-guides/health
     */
    @GetMapping("/health")
    public ApiResponse<String> health() {
        return ApiResponse.<String>builder()
                .message("User Guide service is healthy")
                .result("OK")
                .build();
    }
}
