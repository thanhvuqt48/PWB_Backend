package com.fpt.producerworkbench.configuration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

/**
 * Spring AI ChatModel implementation wrapping Gemini REST API
 * 
 * This adapter enables Spring AI ChatClient to use Google's Gemini API
 * through direct REST calls (no official Spring AI Gemini integration yet)
 */
@Slf4j
@Component
public class GeminiChatModel implements ChatModel {
    
    private final GeminiConfig geminiConfig;
    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;
    
    public GeminiChatModel(
            GeminiConfig geminiConfig,
            WebClient.Builder webClientBuilder,
            ObjectMapper objectMapper) {
        this.geminiConfig = geminiConfig;
        this.webClientBuilder = webClientBuilder;
        this.objectMapper = objectMapper;
        
        log.info("✅ GeminiChatModel initialized with model: {}", geminiConfig.getModel());
    }
    
    /**
     * Synchronous chat completion call
     */
    @Override
    public ChatResponse call(Prompt prompt) {
        try {
            // Extract user message from prompt
            String userMessage = extractUserMessage(prompt);
            
            log.debug("🤖 Gemini ChatModel call:");
            log.debug("   Message length: {} chars", userMessage.length());
            
            // Build Gemini API request
            Map<String, Object> requestBody = buildGeminiRequest(userMessage);
            
            // Call Gemini API
            String responseText = callGeminiApi(requestBody);
            
            // Parse response
            String generatedText = parseGeminiResponse(responseText);
            
            log.debug("✅ Gemini response: {} chars", generatedText.length());
            
            // Convert to Spring AI ChatResponse
            return new ChatResponse(List.of(
                new Generation(new AssistantMessage(generatedText))
            ));
            
        } catch (Exception e) {
            log.error("❌ Gemini ChatModel error: {}", e.getMessage(), e);
            throw new RuntimeException("Gemini API call failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Streaming chat completion (not implemented yet)
     */
    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        throw new UnsupportedOperationException(
            "Streaming not yet supported for Gemini ChatModel. " +
            "Use call() method for synchronous responses."
        );
    }
    
    /**
     * Get default chat options (Gemini uses config-based options)
     */
    @Override
    public org.springframework.ai.chat.prompt.ChatOptions getDefaultOptions() {
        // Return null - Gemini configuration is handled via GeminiConfig
        return null;
    }
    
    /**
     * Extract user message from Spring AI Prompt
     */
    private String extractUserMessage(Prompt prompt) {
        List<Message> messages = prompt.getInstructions();
        
        if (messages == null || messages.isEmpty()) {
            throw new IllegalArgumentException("Prompt must contain at least one message");
        }
        
        // Concatenate all messages (for now - could be smarter)
        StringBuilder combined = new StringBuilder();
        for (Message message : messages) {
            if (combined.length() > 0) {
                combined.append("\n\n");
            }
            combined.append(message.getContent());
        }
        
        return combined.toString();
    }
    
    /**
     * Build Gemini API request body (matches existing format)
     */
    private Map<String, Object> buildGeminiRequest(String userMessage) {
        return Map.of(
            "contents", List.of(
                Map.of("parts", List.of(
                    Map.of("text", userMessage)
                ))
            )
        );
    }
    
    /**
     * Call Gemini API via WebClient (same as existing services)
     */
    private String callGeminiApi(Map<String, Object> requestBody) {
        WebClient webClient = webClientBuilder.build();
        
        String apiUrl = geminiConfig.buildGenerateContentUrl();
        
        log.debug("   Calling Gemini API: {}", apiUrl);
        
        return webClient.post()
            .uri(apiUrl)
            .header("Content-Type", "application/json")
            .header("X-goog-api-key", geminiConfig.getApiKey())
            .bodyValue(requestBody)
            .retrieve()
            .bodyToMono(String.class)
            .block();
    }
    
    /**
     * Parse Gemini API response (matches existing error handling)
     */
    private String parseGeminiResponse(String responseText) throws Exception {
        JsonNode responseJson = objectMapper.readTree(responseText);
        
        // Check for API errors
        if (responseJson.has("error")) {
            JsonNode error = responseJson.get("error");
            String errorMessage = error.path("message").asText("Unknown error");
            int errorCode = error.path("code").asInt(0);
            
            log.error("❌ Gemini API error ({}): {}", errorCode, errorMessage);
            throw new RuntimeException("Gemini API error: " + errorMessage);
        }
        
        // Check if candidates exist
        if (!responseJson.has("candidates") || responseJson.get("candidates").isEmpty()) {
            log.error("❌ No candidates in Gemini response");
            throw new RuntimeException("No response candidates from Gemini");
        }
        
        JsonNode firstCandidate = responseJson.path("candidates").get(0);
        
        // Check for blocked content
        if (firstCandidate.has("finishReason")) {
            String finishReason = firstCandidate.get("finishReason").asText();
            if (!"STOP".equals(finishReason)) {
                log.warn("⚠️ Content blocked or incomplete. Finish reason: {}", finishReason);
            }
        }
        
        // Extract text content
        JsonNode content = firstCandidate.path("content");
        JsonNode parts = content.path("parts");
        
        if (parts.isEmpty()) {
            log.error("❌ No content parts in Gemini response");
            throw new RuntimeException("No content parts in Gemini response");
        }
        
        String text = parts.get(0).path("text").asText();
        
        if (text == null || text.isBlank()) {
            log.error("❌ Empty text in Gemini response");
            throw new RuntimeException("Empty text in Gemini response");
        }
        
        return text;
    }
}
