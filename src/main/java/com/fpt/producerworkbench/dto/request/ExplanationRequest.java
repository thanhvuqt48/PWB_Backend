package com.fpt.producerworkbench.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request để lấy explanation cho music term được highlight
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExplanationRequest {
    
    /**
     * Text được highlight bởi user (music term cần giải thích)
     */
    private String highlightedText;
    
    /**
     * Context xung quanh (optional - để improve accuracy)
     */
    private String contextText;
    
    /**
     * Số lượng related terms cần trả về (default 3)
     */
    @Builder.Default
    private Integer maxRelatedTerms = 3;
    
    /**
     * Ngôn ngữ explanation (default: vi)
     */
    @Builder.Default
    private String language = "vi";
}
