package com.fpt.producerworkbench.dto.response;

import com.fpt.producerworkbench.entity.userguide.GuideCategory;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.Map;

@Setter
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserGuideStatsResponse {

    Long totalGuides;
    Long activeGuides;
    Long inactiveGuides;

    // By category
    Map<GuideCategory, Long> guidesByCategory;

    // By difficulty
    Map<String, Long> guidesByDifficulty;

    // Engagement stats
    Long totalViews;
    Long totalHelpfulCount;
    Double averageViewsPerGuide;
    Double averageHelpfulPerGuide;

    // Most popular
    UserGuideResponse mostViewedGuide;
    UserGuideResponse mostHelpfulGuide;

    // Pinecone stats
    Long vectorsIndexed;
    String pineconeNamespace;
}
