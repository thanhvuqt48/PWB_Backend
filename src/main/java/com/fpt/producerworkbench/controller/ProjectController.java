package com.fpt.producerworkbench.controller;

import com.fpt.producerworkbench.common.ProjectStatus;
import com.fpt.producerworkbench.common.UserRole;
import com.fpt.producerworkbench.dto.request.ProjectCreateRequest;
import com.fpt.producerworkbench.dto.request.UpdateProjectMemberRoleRequest;
import com.fpt.producerworkbench.dto.response.ApiResponse;
import com.fpt.producerworkbench.dto.response.ProjectDetailResponse;
import com.fpt.producerworkbench.dto.response.ProjectMemberResponse;
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

/**
 * Controller quản lý các thao tác liên quan đến project.
 * Bao gồm: tạo project, xem chi tiết project, lấy danh sách project của người dùng,
 * quản lý thành viên project (xem, xóa, cập nhật vai trò), và kiểm tra quyền truy cập.
 */
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
    private final UserRepository userRepository;

    /**
     * Tạo project mới.
     * Chỉ Producer hoặc Admin mới có thể tạo project. Tài khoản phải được verify trước khi tạo project.
     * Tự động tạo ProjectMember với role OWNER.
     */
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

        // Kiểm tra tài khoản đã được verify chưa
        if (user.getIsVerified() == null || !user.getIsVerified()) {
            throw new AppException(ErrorCode.ACCOUNT_NOT_VERIFIED);
        }

        Project createdProject = projectService.createProject(request, creatorEmail);

        ProjectResponse projectResponse = projectMapper.toProjectResponse(createdProject);

        ApiResponse<ProjectResponse> response = ApiResponse.<ProjectResponse>builder()
                .message("Dự án đã được tạo thành công.")
                .result(projectResponse)
                .build();

        return ResponseEntity.ok(response);
    }

    /**
     * Lấy thông tin chi tiết của project.
     * Yêu cầu đăng nhập và có quyền truy cập project.
     */
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


    /**
     * Lấy danh sách project của người dùng hiện tại.
     * Hỗ trợ tìm kiếm theo tên, lọc theo trạng thái, và phân trang. Mặc định sắp xếp theo updatedAt DESC.
     */
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

    /**
     * Lấy danh sách thành viên của project.
     * Yêu cầu đăng nhập và có quyền xem project.
     */
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

    /**
     * Xóa thành viên khỏi project.
     * Yêu cầu đăng nhập và có quyền quản lý thành viên.
     */
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

    /**
     * Cập nhật vai trò của thành viên trong project.
     * Yêu cầu đăng nhập và có quyền quản lý thành viên.
     */
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

    /**
     * Lấy danh sách quyền của người dùng hiện tại đối với project.
     * Trả về các quyền như: xem project, chỉnh sửa project, quản lý thành viên, contract, addendum, payment, v.v.
     * Bao gồm cả quyền addendum: canCreateAddendum, canViewAddendum, canInviteToSign, canDeclineAddendum, canEditAddendum, canCreateAddendumPayment.
     */
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
}