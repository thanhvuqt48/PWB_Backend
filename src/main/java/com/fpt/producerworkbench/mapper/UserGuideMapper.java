package com.fpt.producerworkbench.mapper;

import com.fpt.producerworkbench.dto.response.GuideStepResponse;
import com.fpt.producerworkbench.dto.response.UserGuideResponse;
import com.fpt.producerworkbench.dto.response.UserGuideSummaryResponse;
import com.fpt.producerworkbench.entity.userguide.GuideStep;
import com.fpt.producerworkbench.entity.userguide.UserGuide;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class UserGuideMapper {

    public UserGuideResponse toResponse(UserGuide entity) {
        if (entity == null) {
            return null;
        }

        return UserGuideResponse.builder()
                .id(entity.getId())
                .title(entity.getTitle())
                .shortDescription(entity.getShortDescription())
                .category(entity.getCategory())
                .difficulty(entity.getDifficulty())
                .contentText(entity.getContentText())
                .prerequisites(entity.getPrerequisites())
                .tags(entity.getTags() != null ? List.of(entity.getTags()) : List.of())
                .keywords(entity.getKeywords() != null ? List.of(entity.getKeywords()) : List.of())
                .searchableQueries(entity.getSearchableQueries() != null ? List.of(entity.getSearchableQueries()) : List.of()) // ⭐ CRITICAL FIX!
                .relatedGuideIds(entity.getRelatedGuideIds() != null ? List.of(entity.getRelatedGuideIds()) : List.of())
                .coverImageUrl(entity.getCoverImageUrl())
                .pineconeVectorId(entity.getPineconeVectorId())
                .viewCount(entity.getViewCount())
                .helpfulCount(entity.getHelpfulCount())
                .author(entity.getAuthor())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .version(entity.getVersion())
                .isActive(entity.getIsActive())
                .steps(entity.getSteps() != null ? 
                    entity.getSteps().stream()
                        .map(this::toStepResponse)
                        .collect(Collectors.toList()) : 
                    List.of())
                .build();
    }

    public UserGuideResponse toResponseWithScore(UserGuide entity, Double score) {
        UserGuideResponse response = toResponse(entity);
        if (response != null) {
            response.setRelevanceScore(score);
        }
        return response;
    }

    public GuideStepResponse toStepResponse(GuideStep entity) {
        if (entity == null) {
            return null;
        }

        return GuideStepResponse.builder()
                .id(entity.getId())
                .stepOrder(entity.getStepOrder())
                .title(entity.getTitle())
                .description(entity.getDescription())
                .screenLocation(entity.getScreenLocation())
                .uiElement(entity.getUiElement())
                .expectedResult(entity.getExpectedResult())
                .screenshotUrl(entity.getScreenshotUrl())
                .videoUrl(entity.getVideoUrl())
                .tips(entity.getTips())
                .commonMistakes(entity.getCommonMistakes())
                .build();
    }

    public List<UserGuideResponse> toResponseList(List<UserGuide> entities) {
        if (entities == null) {
            return List.of();
        }
        return entities.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * Convert to lightweight summary (for listing)
     */
    public UserGuideSummaryResponse toSummaryResponse(UserGuide entity) {
        if (entity == null) {
            return null;
        }

        return UserGuideSummaryResponse.builder()
                .id(entity.getId())
                .title(entity.getTitle())
                .shortDescription(entity.getShortDescription())
                .category(entity.getCategory())
                .difficulty(entity.getDifficulty())
                .coverImageUrl(entity.getCoverImageUrl())
                .viewCount(entity.getViewCount())
                .helpfulCount(entity.getHelpfulCount())
                .totalSteps(entity.getSteps() != null ? entity.getSteps().size() : 0)
                .author(entity.getAuthor())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .isActive(entity.getIsActive())
                .build();
    }

    public List<UserGuideSummaryResponse> toSummaryResponseList(List<UserGuide> entities) {
        if (entities == null) {
            return List.of();
        }
        return entities.stream()
                .map(this::toSummaryResponse)
                .collect(Collectors.toList());
    }
}
