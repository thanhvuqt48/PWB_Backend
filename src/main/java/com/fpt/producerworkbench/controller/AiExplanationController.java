package com.fpt.producerworkbench.controller;

import com.fpt.producerworkbench.dto.request.ExplanationRequest;
import com.fpt.producerworkbench.dto.response.ApiResponse;
import com.fpt.producerworkbench.dto.response.ExplanationResponse;
import com.fpt.producerworkbench.exception.AppException;
import com.fpt.producerworkbench.exception.ErrorCode;
import com.fpt.producerworkbench.service.AiExplanationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * REST API cho AI-powered music terms explanation
 * 
 * Endpoint chính để frontend gọi khi user highlight text
 */
@Slf4j
@RestController
@RequestMapping("/api/ai/explanation")
@RequiredArgsConstructor
public class AiExplanationController {
    
    private final AiExplanationService aiExplanationService;
    
    /**
     * Generate Vietnamese explanation for highlighted music production term
     * 
     * POST /api/ai/explanation
     * 
     * Request body:
     * {
     *   "highlightedText": "reverb",
//     *   "contextText": "tôi muốn thêm reverb cho vocal",
     *   "maxRelatedTerms": 3,
     *   "language": "vi"
     * }
     * 
     * Response:
     * {
     *   "code": 200,
     *   "message": "Tạo giải thích thành công",
     *   "result": {
     *     "originalText": "reverb",
     *     "explanation": "Reverb là hiệu ứng âm thanh...",
     *     "relatedTerms": [...],
     *     "foundInDatabase": true,
     *     "processingTimeMs": 1250,
     *     "model": "gemini-2.5-flash"
     *   }
     * }
     */
    @PostMapping
    public ApiResponse<ExplanationResponse> getExplanation(
            @RequestBody ExplanationRequest request) {
        
        log.info("📝 Received explanation request for: '{}'", request.getHighlightedText());
        
        // Validate request
        if (request.getHighlightedText() == null || request.getHighlightedText().trim().isEmpty()) {
            throw new AppException(ErrorCode.INVALID_EXPLANATION_REQUEST);
        }
        
        try {
            ExplanationResponse response = aiExplanationService.generateExplanation(request);
            
            return ApiResponse.<ExplanationResponse>builder()
                    .code(200)
                    .message("Tạo giải thích thành công")
                    .result(response)
                    .build();
            
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            log.error("❌ Error generating explanation: {}", e.getMessage(), e);
            
            // Check if it's a Gemini API error (rate limit or API error)
            if (e.getMessage() != null && e.getMessage().contains("429")) {
                throw new AppException(ErrorCode.GEMINI_API_RATE_LIMIT);
            } else if (e.getMessage() != null && e.getMessage().contains("Gemini")) {
                throw new AppException(ErrorCode.GEMINI_API_ERROR);
            }
            
            throw new AppException(ErrorCode.AI_SERVICE_UNAVAILABLE);
        }
    }
    
    /**
     * Health check endpoint
     */
    @GetMapping("/health")
    public ApiResponse<String> health() {
        return ApiResponse.<String>builder()
                .code(200)
                .message("AI Explanation Service đang hoạt động")
                .result("Service is running")
                .build();
    }
}
