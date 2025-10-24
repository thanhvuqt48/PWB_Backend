package com.fpt.producerworkbench.dto.response;

import com.fpt.producerworkbench.common.ProjectRole;
import com.fpt.producerworkbench.common.ProjectStatus;
import com.fpt.producerworkbench.common.ProjectType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectSummaryResponse {
    private Long id;
    private String title;
    private String description;
    private ProjectStatus status;
    private ProjectType type;
    private ProjectRole myRole;
    private String creatorName;
    private Date createdAt;
}