package com.fpt.producerworkbench.dto.request;

import com.fpt.producerworkbench.entity.userguide.GuideCategory;
import com.fpt.producerworkbench.entity.userguide.GuideDifficulty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Setter
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserGuideSearchRequest {

    @NotBlank(message = "Search query is required")
    String query;

    // Optional filters
    GuideCategory category;

    GuideDifficulty difficulty;

    // Pinecone search params
    @Min(value = 1, message = "topK must be at least 1")
    @Max(value = 20, message = "topK must not exceed 20")
    @Builder.Default
    Integer topK = 5;

    @Min(value = 0, message = "minScore must be between 0 and 1")
    @Max(value = 1, message = "minScore must be between 0 and 1")
    @Builder.Default
    Double minScore = 0.6;

    // Include inactive guides?
    @Builder.Default
    Boolean includeInactive = false;
}
