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
public class AIContextualResponse {

    // AI-generated answer
    String answer;

    // Detected intent
    String intent; // "how-to", "troubleshoot", "find-feature", "best-practice"

    // Confidence score
    Double confidence;

    // Relevant guides used to generate answer
    List<UserGuideResponse> relevantGuides;

    // Suggested next actions
    List<String> suggestedActions;

    // Related topics user might be interested in
    List<String> relatedTopics;

    // Processing metadata
    Long processingTimeMs;
    String model; // e.g., "gemini-1.5-flash"
}
