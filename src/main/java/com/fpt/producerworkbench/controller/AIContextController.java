package com.fpt.producerworkbench.controller;

import com.fpt.producerworkbench.dto.request.AIContextRequest;
import com.fpt.producerworkbench.dto.response.AIContextualResponse;
import com.fpt.producerworkbench.dto.response.ApiResponse;
import com.fpt.producerworkbench.dto.response.ConversationHistoryResponse;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import java.util.List;
import java.util.stream.Collectors;
import java.util.ArrayList;
import com.fpt.producerworkbench.exception.AppException;
import com.fpt.producerworkbench.exception.ErrorCode;
import com.fpt.producerworkbench.service.AIContextService;
import com.fpt.producerworkbench.utils.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

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
    private final SecurityUtils securityUtils;
    private final ChatMemory redisChatMemory;

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
        
        // Get current user ID from JWT
        Long userId = securityUtils.getCurrentUserId();
        
        log.info("🔑 USER ID from JWT: {}", userId);
        log.info("📝 Query: {}", request.getQuery());
        
        // ALWAYS auto-generate sessionId from userId (frontend không cần gửi)
        String sessionId = generateSessionId(userId);
        request.setSessionId(sessionId);
        
        log.info("✅ Auto-generated sessionId: {}", sessionId);
        
        // Auto-fill user role from JWT
        if (request.getUserRole() == null && authentication != null) {
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
        
        // Get user ID for session
        Long userId = securityUtils.getCurrentUserId();
        
        log.info("Quick help request from user {}: {}", userId, query);
        
        AIContextRequest request = AIContextRequest.builder()
                .query(query)
                .includeRelatedGuides(true)
                .maxGuides(2)
                .sessionId(generateSessionId(userId))  // Generate session for ChatClient
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
     * Get conversation history from Redis
     * GET /api/ai/context/history
     */
    @GetMapping("/history")
    public ApiResponse<ConversationHistoryResponse> getHistory() {
        Long userId = securityUtils.getCurrentUserId();
        String sessionId = generateSessionId(userId);
        
        log.info("📜 Fetching conversation history for user: {}, sessionId: {}", userId, sessionId);
        
        try {
            // Get messages from Redis (last 50 messages)
            List<Message> messages = redisChatMemory.get(sessionId, 50);
            
            // Convert Spring AI Messages to DTOs for frontend
            List<ConversationHistoryResponse.MessageDTO> messageDTOs = messages.stream()
                .map(msg -> ConversationHistoryResponse.MessageDTO.builder()
                    .role(msg instanceof UserMessage ? "user" : "assistant")
                    .content(msg.getContent())
                    .build())
                .collect(Collectors.toList());
            
            ConversationHistoryResponse response = ConversationHistoryResponse.builder()
                .sessionId(sessionId)
                .messages(messageDTOs)
                .totalMessages(messageDTOs.size())
                .build();
            
            log.info("   ✅ Loaded {} messages from Redis", messageDTOs.size());
            
            return ApiResponse.<ConversationHistoryResponse>builder()
                .message("History loaded successfully")
                .result(response)
                .build();
        } catch (Exception e) {
            log.error("❌ Failed to load history: {}", e.getMessage(), e);
            // Return empty history on error
            return ApiResponse.<ConversationHistoryResponse>builder()
                .message("No history found")
                .result(ConversationHistoryResponse.builder()
                    .sessionId(sessionId)
                    .messages(new ArrayList<>())
                    .totalMessages(0)
                    .build())
                .build();
        }
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
    
    /**
     * Generate session ID for conversation tracking
     * Format: "ai-chat:user{userId}:{date}"
     * Each user gets a new session per day
     */
    private String generateSessionId(Long userId) {
        String date = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        return String.format("ai-chat:user%d:%s", userId, date);
    }
    
    /**
     * Validate that session ID belongs to the current user
     * Prevents users from accessing other users' conversations
     */
    private void validateSessionOwnership(String sessionId, Long userId) {
        String expectedPrefix = "ai-chat:user" + userId + ":";
        if (!sessionId.startsWith(expectedPrefix)) {
            log.warn("Session ownership validation failed. User {} tried to access session: {}", 
                    userId, sessionId);
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
    }
}
