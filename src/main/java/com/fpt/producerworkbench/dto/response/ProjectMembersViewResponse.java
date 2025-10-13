package com.fpt.producerworkbench.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectMembersViewResponse {
    private PageResponse<ProjectMemberResponse> members;
    private Integer anonymousCollaboratorCount;
    private String anonymousSummaryMessage;
}



