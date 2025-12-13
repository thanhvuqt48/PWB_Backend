package com.fpt.producerworkbench.dto.response;

import lombok.*;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectReviewResponse {
    private Long id;
    private Long projectId;
    private String projectTitle;
    private String producerName;
    private Integer rating;
    private String comment;
    private boolean allowPublicPortfolio;
    private LocalDateTime createdAt;
}