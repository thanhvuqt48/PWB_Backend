package com.fpt.producerworkbench.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Gemini AI API Configuration
 * Supports both chat completion and embeddings
 * 
 * Security: API key is passed via header (X-goog-api-key), NOT in URL
 * Free tier limits: 15 requests/min, 1500 requests/day
 * Get API key at: https://aistudio.google.com/app/apikey
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "gemini")
public class GeminiConfig {
    
    private String apiKey;
    private String model; // gemini-2.0-flash or gemini-2.0-flash-exp  
    private String embeddingModel; // text-embedding-004
    private Integer maxTokens = 2048;
    private Double temperature = 0.3;
    private String baseUrl = "https://generativelanguage.googleapis.com/v1beta";
    
    /**
     * Build URL for chat/text generation (WITHOUT API key)
     * API key must be passed via X-goog-api-key header for security
     */
    public String buildGenerateContentUrl() {
        return String.format("%s/models/%s:generateContent", 
                baseUrl, model);
    }
    
    /**
     * Build URL for single embedding (WITHOUT API key)
     * API key must be passed via X-goog-api-key header for security
     */
    public String buildEmbeddingUrl() {
        return String.format("%s/models/%s:embedContent", 
                baseUrl, embeddingModel);
    }
    
    /**
     * Build URL for batch embeddings (WITHOUT API key)
     * API key must be passed via X-goog-api-key header for security
     * Use this to avoid rate limits - 1 request instead of N
     */
    public String buildBatchEmbeddingUrl() {
        return String.format("%s/models/%s:batchEmbedContents", 
                baseUrl, embeddingModel);
    }
    
    /**
     * Get embedding dimension (768 for text-embedding-004)
     */
    public int getEmbeddingDimension() {
        return 768;
    }
}
