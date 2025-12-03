package com.fpt.producerworkbench.dto.response;

import com.fpt.producerworkbench.entity.userguide.GuideCategory;
import com.fpt.producerworkbench.entity.userguide.GuideDifficulty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Lightweight summary response for listing guides
 * Excludes steps, full content, and detailed metadata
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserGuideSummaryResponse {
    
    private Long id;
    private String title;
    private String shortDescription;
    private GuideCategory category;
    private GuideDifficulty difficulty;
    private String coverImageUrl;
    private Integer viewCount;
    private Integer helpfulCount;
    private Integer totalSteps;
    private String author;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Boolean isActive;
}
