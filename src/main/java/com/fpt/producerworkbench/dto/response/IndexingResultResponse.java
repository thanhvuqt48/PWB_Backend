package com.fpt.producerworkbench.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Setter
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class IndexingResultResponse {

    Boolean success;
    String message;
    Long guideId;
    String pineconeVectorId;
    String coverImageUrl;
    Integer totalSteps;
    Long processingTimeMs;
}
