package com.fpt.producerworkbench.controller;

import com.fpt.producerworkbench.dto.response.ApiResponse;
import com.fpt.producerworkbench.dto.response.ProjectMembersViewResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import com.fpt.producerworkbench.service.ProjectMemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/v1/projects/{projectId}/members")
@RequiredArgsConstructor
public class ProjectMemberController {

    private final ProjectMemberService projectMemberService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<ProjectMembersViewResponse>> getProjectMembers(@PathVariable Long projectId,
                                                                                     @AuthenticationPrincipal Jwt jwt,
                                                                                     Pageable pageable) {
        String viewerEmail = jwt.getSubject();
        ProjectMembersViewResponse view = projectMemberService.getProjectMembersForViewer(projectId, viewerEmail, pageable);
        return ResponseEntity.ok(ApiResponse.<ProjectMembersViewResponse>builder()
                .result(view)
                .build());
    }
}