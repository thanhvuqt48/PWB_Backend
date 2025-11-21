package com.fpt.producerworkbench.controller;

import com.fpt.producerworkbench.dto.request.UpdateProjectMemberRoleRequest;
import com.fpt.producerworkbench.dto.response.ApiResponse;
import com.fpt.producerworkbench.dto.response.ProjectMemberResponse;
import com.fpt.producerworkbench.dto.response.ProjectMembersViewResponse;
import com.fpt.producerworkbench.exception.AppException;
import com.fpt.producerworkbench.exception.ErrorCode;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import com.fpt.producerworkbench.service.ProjectMemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;


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

    @DeleteMapping("/{userId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> removeProjectMember(@PathVariable Long projectId,
                                                                 @PathVariable Long userId,
                                                                 Authentication authentication) {
        if (projectId == null || projectId <= 0 || userId == null || userId <= 0) {
            throw new AppException(ErrorCode.INVALID_PARAMETER_FORMAT);
        }

        projectMemberService.removeProjectMember(projectId, userId, authentication);

        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .message("Xóa thành viên khỏi dự án thành công")
                .build());
    }

    @PatchMapping("/{userId}/role")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<ProjectMemberResponse>> updateProjectMemberRole(@PathVariable Long projectId,
                                                                                      @PathVariable Long userId,
                                                                                      @Valid @RequestBody UpdateProjectMemberRoleRequest request,
                                                                                      Authentication authentication) {
        if (projectId == null || projectId <= 0 || userId == null || userId <= 0) {
            throw new AppException(ErrorCode.INVALID_PARAMETER_FORMAT);
        }

        ProjectMemberResponse updatedMember = projectMemberService
                .updateProjectMemberRole(projectId, userId, request, authentication);

        return ResponseEntity.ok(ApiResponse.<ProjectMemberResponse>builder()
                .message("Cập nhật vai trò thành viên thành công")
                .result(updatedMember)
                .build());
    }
}