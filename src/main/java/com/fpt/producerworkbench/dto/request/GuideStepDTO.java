package com.fpt.producerworkbench.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Setter
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class GuideStepDTO {

    @NotNull(message = "Step order is required")
    @Positive(message = "Step order must be positive")
    Integer stepOrder;

    @NotBlank(message = "Title is required")
    @Size(max = 255, message = "Title must not exceed 255 characters")
    String title;

    @NotBlank(message = "Description is required")
    String description;

    // UI Context
    @Size(max = 255, message = "Screen location must not exceed 255 characters")
    String screenLocation;

    @Size(max = 255, message = "UI element must not exceed 255 characters")
    String uiElement;

    String expectedResult;

    // Visual Aids
    String screenshotUrl; // S3 URL or will be uploaded

    String videoUrl;

    // Tips
    String tips;

    String commonMistakes;
}
