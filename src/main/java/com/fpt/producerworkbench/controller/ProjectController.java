package com.fpt.producerworkbench.controller;

import com.fpt.producerworkbench.common.ProjectStatus;
import com.fpt.producerworkbench.common.UserRole;
import com.fpt.producerworkbench.dto.request.ProjectCreateRequest;
import com.fpt.producerworkbench.dto.request.UpdateProjectMemberRoleRequest;
import com.fpt.producerworkbench.dto.response.ApiResponse;
import com.fpt.producerworkbench.dto.response.ProjectDetailResponse;
import com.fpt.producerworkbench.dto.response.ProjectExpenseChartResponse;
import com.fpt.producerworkbench.dto.response.ProjectExpenseDetailResponse;
import com.fpt.producerworkbench.dto.response.ProjectMemberResponse;
import com.fpt.producerworkbench.dto.response.ProjectMoneySplitDetailResponse;
import com.fpt.producerworkbench.dto.response.ProjectMembersViewResponse;
import com.fpt.producerworkbench.dto.response.ProjectPermissionResponse;
import com.fpt.producerworkbench.dto.response.ProjectResponse;
import com.fpt.producerworkbench.dto.response.ProjectSummaryResponse;
import com.fpt.producerworkbench.entity.Project;
import com.fpt.producerworkbench.entity.User;
import com.fpt.producerworkbench.exception.AppException;
import com.fpt.producerworkbench.exception.ErrorCode;
import com.fpt.producerworkbench.mapper.ProjectMapper;
import com.fpt.producerworkbench.repository.UserRepository;
import com.fpt.producerworkbench.service.MyProjectsService;
import com.fpt.producerworkbench.service.ProjectDetailService;
import com.fpt.producerworkbench.service.ProjectExpenseService;
import com.fpt.producerworkbench.service.ProjectMemberService;
import com.fpt.producerworkbench.service.ProjectPermissionService;
import com.fpt.producerworkbench.service.ProjectService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;
    private final ProjectMapper projectMapper;
    private final ProjectDetailService projectDetailService;
    private final MyProjectsService myProjectsService;
    private final ProjectMemberService projectMemberService;
    private final ProjectPermissionService projectPermissionService;
    private final ProjectExpenseService projectExpenseService;
    private final UserRepository userRepository;

    @PostMapping("/projects")
    public ResponseEntity<ApiResponse<ProjectResponse>> createProject(
            @Valid @RequestBody ProjectCreateRequest request,
            @AuthenticationPrincipal Jwt jwt,
            Authentication auth) {

        String creatorEmail = jwt.getSubject();
        User user = userRepository.findByEmail(creatorEmail)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        
        UserRole userRole = user.getRole();
        if (userRole != UserRole.PRODUCER && userRole != UserRole.ADMIN) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }

        Project createdProject = projectService.createProject(request, creatorEmail);

        ProjectResponse projectResponse = projectMapper.toProjectResponse(createdProject);

        ApiResponse<ProjectResponse> response = ApiResponse.<ProjectResponse>builder()
                .message("Dự án đã được tạo thành công.")
                .result(projectResponse)
                .build();

        return ResponseEntity.ok(response);
    }

    @GetMapping("/projects/{projectId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<ProjectDetailResponse>> getProjectDetail(
            @PathVariable Long projectId,
            Authentication auth) {
        
        ProjectDetailResponse projectDetail = projectDetailService.getProjectDetail(auth, projectId);
        
        return ResponseEntity.ok(ApiResponse.<ProjectDetailResponse>builder()
                .code(200)
                .result(projectDetail)
                .build());
    }

    @GetMapping("/my-projects")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Page<ProjectSummaryResponse>>> getMyProjects(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) ProjectStatus status,
            @PageableDefault(sort = "updatedAt", direction = Sort.Direction.DESC) Pageable pageable) {

        User currentUser = userRepository.findByEmail(jwt.getSubject())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        Page<ProjectSummaryResponse> myProjects = myProjectsService.getMyProjects(currentUser, search, status, pageable);

        return ResponseEntity.ok(ApiResponse.<Page<ProjectSummaryResponse>>builder()
                .result(myProjects)
                .build());
    }

    @GetMapping("/projects/{projectId}/members")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<ProjectMembersViewResponse>> getProjectMembers(
            @PathVariable Long projectId,
            @AuthenticationPrincipal Jwt jwt,
            Pageable pageable) {
        String viewerEmail = jwt.getSubject();
        ProjectMembersViewResponse view = projectMemberService.getProjectMembersForViewer(projectId, viewerEmail, pageable);
        return ResponseEntity.ok(ApiResponse.<ProjectMembersViewResponse>builder()
                .result(view)
                .build());
    }

    @DeleteMapping("/projects/{projectId}/members/{userId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> removeProjectMember(
            @PathVariable Long projectId,
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

    @PatchMapping("/projects/{projectId}/members/{userId}/role")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<ProjectMemberResponse>> updateProjectMemberRole(
            @PathVariable Long projectId,
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

    @GetMapping("/projects/{projectId}/permissions")
    public ResponseEntity<ApiResponse<ProjectPermissionResponse>> getProjectPermissions(
            @PathVariable Long projectId,
            Authentication auth) {
        var permissions = projectPermissionService.checkProjectPermissions(auth, projectId);
        return ResponseEntity.ok(ApiResponse.<ProjectPermissionResponse>builder()
                .code(200)
                .result(permissions)
                .build());
    }

    @GetMapping("/projects/{projectId}/expense-chart")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<ProjectExpenseChartResponse>> getProjectExpenseChart(
            @PathVariable Long projectId,
            Authentication auth) {
        
        ProjectExpenseChartResponse chartData = projectExpenseService.getProjectExpenseChart(projectId, auth);
        
        return ResponseEntity.ok(ApiResponse.<ProjectExpenseChartResponse>builder()
                .code(200)
                .message("Lấy thống kê chi phí dự án thành công")
                .result(chartData)
                .build());
    }

    @GetMapping("/projects/{projectId}/expense-details")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<ProjectExpenseDetailResponse>>> getProjectExpenseDetails(
            @PathVariable Long projectId,
            Authentication auth) {
        
        java.util.List<ProjectExpenseDetailResponse> details = projectExpenseService.getProjectExpenseDetails(projectId, auth);
        
        return ResponseEntity.ok(ApiResponse.<List<ProjectExpenseDetailResponse>>builder()
                .code(200)
                .message("Lấy chi tiết chi phí dịch vụ thành công")
                .result(details)
                .build());
    }

    @GetMapping("/projects/{projectId}/money-split-details")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<ProjectMoneySplitDetailResponse>>> getProjectMoneySplitDetails(
            @PathVariable Long projectId,
            Authentication auth) {
        
        java.util.List<ProjectMoneySplitDetailResponse> details = projectExpenseService.getProjectMoneySplitDetails(projectId, auth);
        
        return ResponseEntity.ok(ApiResponse.<List<ProjectMoneySplitDetailResponse>>builder()
                .code(200)
                .message("Lấy chi tiết chi phí chia tiền thành viên thành công")
                .result(details)
                .build());
    }
}