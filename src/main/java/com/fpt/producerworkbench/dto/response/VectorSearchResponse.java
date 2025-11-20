package com.fpt.producerworkbench.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response for vector similarity search
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VectorSearchResponse {
    
    /**
     * Original query text
     */
    private String query;
    
    /**
     * List of similar terms found
     */
    private List<SimilarTerm> results;
    
    /**
     * Total number of results
     */
    private Integer totalResults;
    
    /**
     * Search processing time in milliseconds
     */
    private Long searchTimeMs;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SimilarTerm {
        private String term;
        private String definition;
        private String category;
        private Double similarityScore;
        private List<String> synonyms;
        private List<String> examples;
    }
}
