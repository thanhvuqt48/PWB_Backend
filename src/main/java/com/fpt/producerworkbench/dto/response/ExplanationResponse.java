package com.fpt.producerworkbench.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response chứa explanation được generate bởi RAG system
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExplanationResponse {
    
    /**
     * Text gốc được highlight
     */
    private String originalText;
    
    /**
     * Explanation được generate bởi Gemini (tiếng Việt)
     */
    private String explanation;
    
    /**
     * Danh sách terms liên quan từ vector search
     */
    private List<RelatedTerm> relatedTerms;
    
    /**
     * Thời gian xử lý (ms)
     */
    private Long processingTimeMs;
    
    /**
     * Có tìm thấy term trong database không
     */
    private Boolean foundInDatabase;
    
    /**
     * Model used for generation
     */
    private String model;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RelatedTerm {
        private String term;
        private String definition;
        private String category;
        private Double similarity;
        private List<String> synonyms;
    }
}
