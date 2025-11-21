package com.fpt.producerworkbench.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for Gemini REST API
 * Free tier limits: 15 requests/min, 1500 requests/day
 * Get API key at: https://aistudio.google.com/app/apikey
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "spring.gemini")
public class GeminiProperties {
    
    /**
     * Gemini API Key
     */
    private String apiKey;
    
    /**
     * Model name (gemini-2.5-flash, gemini-2.5-pro, gemini-2.0-flash)
     */
    private String model = "gemini-2.5-flash";
    
    /**
     * Base URL for Gemini API
     */
    private String baseUrl = "https://generativelanguage.googleapis.com/v1beta";
}
