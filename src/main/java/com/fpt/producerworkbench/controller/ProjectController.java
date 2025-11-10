package com.fpt.producerworkbench.controller;

import com.fpt.producerworkbench.common.UserRole;
import com.fpt.producerworkbench.dto.request.ProjectCreateRequest;
import com.fpt.producerworkbench.dto.response.ApiResponse;
import com.fpt.producerworkbench.dto.response.ProjectDetailResponse;
import com.fpt.producerworkbench.dto.response.ProjectResponse;
import com.fpt.producerworkbench.entity.Project;
import com.fpt.producerworkbench.entity.User;
import com.fpt.producerworkbench.exception.AppException;
import com.fpt.producerworkbench.exception.ErrorCode;
import com.fpt.producerworkbench.mapper.ProjectMapper;
import com.fpt.producerworkbench.repository.UserRepository;
import com.fpt.producerworkbench.service.ProjectDetailService;
import com.fpt.producerworkbench.service.ProjectService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;
    private final ProjectMapper projectMapper;
    private final ProjectDetailService projectDetailService;
    private final UserRepository userRepository;

    @PostMapping
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

    @GetMapping("/{projectId}")
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
}