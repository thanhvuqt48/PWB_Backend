package com.fpt.producerworkbench.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/**
 * Pinecone configuration with namespace support
 * Supports multiple namespaces for different data types:
 * - music-terms: Music terminology and explanations
 * - user-guides: AI-powered user guidance and tutorials
 * - workflows: System workflow instructions (future)
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "pinecone")
public class PineconeNamespaceConfig {
    
    private String apiKey;
    private String indexName;
    private String host;
    private String environment;
    private String projectId;
    private int dimension = 768; // Gemini text-embedding-004 dimension
    
    /**
     * Namespace mappings
     * Key: logical name (e.g., "music-terms", "user-guides")
     * Value: actual namespace in Pinecone
     */
    private Map<String, String> namespaces;
    
    /**
     * Get namespace for music terms
     */
    public String getMusicTermsNamespace() {
        return namespaces.get("music-terms");
    }
    
    /**
     * Get namespace for user guides
     */
    public String getUserGuidesNamespace() {
        return namespaces.get("user-guides");
    }
    
    /**
     * Get namespace by key
     */
    public String getNamespace(String key) {
        return namespaces.getOrDefault(key, key);
    }
}
