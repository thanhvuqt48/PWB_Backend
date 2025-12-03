package com.fpt.producerworkbench.dto.response;

import com.fpt.producerworkbench.entity.userguide.GuideCategory;
import com.fpt.producerworkbench.entity.userguide.GuideDifficulty;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;
import java.util.List;

@Setter
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserGuideResponse {

    Long id;
    String title;
    String shortDescription;
    GuideCategory category;
    GuideDifficulty difficulty;
    String contentText;
    List<String> prerequisites;
    List<String> tags;
    List<String> keywords;
    List<String> searchableQueries; // ⭐ CRITICAL: Must return to frontend for edit!
    List<Long> relatedGuideIds;
    String coverImageUrl;
    String pineconeVectorId;
    Integer viewCount;
    Integer helpfulCount;
    String author;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
    String version;
    Boolean isActive;

    // Steps
    List<GuideStepResponse> steps;

    // Similarity score (only for search results)
    Double relevanceScore;
}
