package com.fpt.producerworkbench.controller;

import com.fpt.producerworkbench.dto.request.AIContextRequest;
import com.fpt.producerworkbench.dto.response.AIContextualResponse;
import com.fpt.producerworkbench.dto.response.ApiResponse;
import com.fpt.producerworkbench.service.AIContextService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * Controller xử lý AI Context - Trả lời câu hỏi dựa trên ngữ cảnh hệ thống
 * 
 * Sử dụng Gemini API + Pinecone Vector Search để:
 * - Phân tích intent của user
 * - Tìm guides liên quan
 * - Generate contextual responses
 */
@RestController
@RequestMapping("/api/ai/context")
@RequiredArgsConstructor
@Slf4j
public class AIContextController {

    private final AIContextService aiContextService;

    /**
     * [PUBLIC - Testing] Get AI-powered contextual guidance
     * POST /api/ai/context/guidance
     * 
     * Example request:
     * {
     *   "query": "Làm sao để tạo dự án mới?",
     *   "currentPage": "/projects",
     *   "includeRelatedGuides": true,
     *   "maxGuides": 3
     * }
     */
    @PostMapping("/guidance")
    public ApiResponse<AIContextualResponse> getGuidance(
            @Valid @RequestBody AIContextRequest request,
            Authentication authentication) {
        
        log.info("AI Context request from user: {} - Query: {}", 
                authentication.getName(), request.getQuery());
        
        // Auto-fill user role from JWT
        if (request.getUserRole() == null && authentication != null) {
            // Extract role from authentication
            String role = authentication.getAuthorities().stream()
                    .findFirst()
                    .map(auth -> auth.getAuthority())
                    .orElse("USER");
            request.setUserRole(role);
        }
        
        AIContextualResponse result = aiContextService.getContextualGuidance(request);
        
        return ApiResponse.<AIContextualResponse>builder()
                .message("AI guidance generated successfully")
                .result(result)
                .build();
    }

    /**
     * [PUBLIC - Testing] Quick help - Simplified version
     * GET /api/ai/context/quick-help?query=...
     */
    @GetMapping("/quick-help")
    public ApiResponse<AIContextualResponse> getQuickHelp(
            @RequestParam String query,
            Authentication authentication) {
        
        log.info("Quick help request: {}", query);
        
        AIContextRequest request = AIContextRequest.builder()
                .query(query)
                .includeRelatedGuides(true)
                .maxGuides(2)
                .build();
        
        // Auto-fill user role
        if (authentication != null) {
            String role = authentication.getAuthorities().stream()
                    .findFirst()
                    .map(auth -> auth.getAuthority())
                    .orElse("USER");
            request.setUserRole(role);
        }
        
        AIContextualResponse result = aiContextService.getContextualGuidance(request);
        
        return ApiResponse.<AIContextualResponse>builder()
                .message("Quick help generated")
                .result(result)
                .build();
    }

    /**
     * Health check
     * GET /api/ai/context/health
     */
    @GetMapping("/health")
    public ApiResponse<String> health() {
        return ApiResponse.<String>builder()
                .message("AI Context service is healthy")
                .result("OK")
                .build();
    }
}
