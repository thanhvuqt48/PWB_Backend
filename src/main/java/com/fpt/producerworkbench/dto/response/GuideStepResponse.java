package com.fpt.producerworkbench.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Setter
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class GuideStepResponse {

    Long id;
    Integer stepOrder;
    String title;
    String description;
    String screenLocation;
    String uiElement;
    String expectedResult;
    String screenshotUrl;
    String videoUrl;
    String tips;
    String commonMistakes;
}
