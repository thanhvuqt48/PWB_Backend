package com.fpt.producerworkbench.service;

import com.fpt.producerworkbench.dto.response.ProjectMemberResponse;
import com.fpt.producerworkbench.dto.response.ProjectMembersViewResponse;
import org.springframework.data.domain.Pageable;
import java.util.List;

public interface ProjectMemberService {
    List<ProjectMemberResponse> getProjectMembers(Long projectId);
    ProjectMembersViewResponse getProjectMembersForViewer(Long projectId, String viewerEmail, Pageable pageable);
}