package com.fpt.producerworkbench.service;

import com.fpt.producerworkbench.dto.request.UpdateProjectMemberRoleRequest;
import com.fpt.producerworkbench.dto.response.ProjectMemberResponse;
import com.fpt.producerworkbench.dto.response.ProjectMembersViewResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import java.util.List;

public interface ProjectMemberService {
    List<ProjectMemberResponse> getProjectMembers(Long projectId);
    ProjectMembersViewResponse getProjectMembersForViewer(Long projectId, String viewerEmail, Pageable pageable);
    void removeProjectMember(Long projectId, Long userId, Authentication auth);
    ProjectMemberResponse updateProjectMemberRole(Long projectId, Long userId, UpdateProjectMemberRoleRequest request, Authentication auth);
}