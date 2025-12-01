package com.fpt.producerworkbench.dto.request;

import com.fpt.producerworkbench.entity.userguide.GuideCategory;
import com.fpt.producerworkbench.entity.userguide.GuideDifficulty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Setter
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserGuideUpdateRequest {

    @Size(max = 255, message = "Title must not exceed 255 characters")
    String title;

    @Size(max = 500, message = "Short description must not exceed 500 characters")
    String shortDescription;

    GuideCategory category;

    GuideDifficulty difficulty;

    String contentText;

    // Prerequisites
    List<String> prerequisites;

    // Metadata
    @Size(max = 20, message = "Maximum 20 tags allowed")
    List<String> tags;

    @Size(max = 50, message = "Maximum 50 keywords allowed")
    List<String> keywords;

    List<Long> relatedGuideIds;
    
    // Searchable queries - Common questions users might ask
    @Size(max = 20, message = "Maximum 20 searchable queries allowed")
    List<String> searchableQueries;

    // Steps - if provided, will replace all existing steps
    @Valid
    List<GuideStepDTO> steps;

    // Images
    String coverImageUrl;

    Boolean isActive;

    @Size(max = 20, message = "Version must not exceed 20 characters")
    String version;
}
