package com.fpt.producerworkbench.dto.response;

import com.fpt.producerworkbench.common.ProjectStatus;
import com.fpt.producerworkbench.common.ProjectType;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ProjectResponse {
    private Long id;
    private String title;
    private String description;
    private ProjectType type;
    private ProjectStatus status;
    private Long creatorId;
}