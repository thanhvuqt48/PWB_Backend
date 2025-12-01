package com.fpt.producerworkbench.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Setter
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserGuideSearchResponse {

    String query;
    Integer totalResults;
    Long processingTimeMs;
    List<UserGuideResponse> guides;

    // Metadata
    String searchStrategy; // e.g., "vector-search", "hybrid-search"
    Double minScore;
    Integer topK;
}
