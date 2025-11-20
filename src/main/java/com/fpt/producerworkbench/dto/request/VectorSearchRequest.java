package com.fpt.producerworkbench.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request for vector similarity search
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VectorSearchRequest {
    
    /**
     * Query text to search for similar terms
     */
    private String query;
    
    /**
     * Number of top results to return (default 5)
     */
    @Builder.Default
    private Integer topK = 5;
    
    /**
     * Minimum similarity score threshold (0.0 - 1.0)
     */
    @Builder.Default
    private Double minScore = 0.0;
    
    /**
     * Filter by category (optional)
     */
    private String category;
}
