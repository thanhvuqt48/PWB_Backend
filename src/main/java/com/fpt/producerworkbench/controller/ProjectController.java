package com.fpt.producerworkbench.controller;

import com.fpt.producerworkbench.dto.request.ProjectCreateRequest;
import com.fpt.producerworkbench.dto.response.ApiResponse;
import com.fpt.producerworkbench.dto.response.ProjectResponse;
import com.fpt.producerworkbench.entity.Project;
import com.fpt.producerworkbench.exception.AppException;
import com.fpt.producerworkbench.exception.ErrorCode;
import com.fpt.producerworkbench.mapper.ProjectMapper;
import com.fpt.producerworkbench.service.ProjectService;
import com.fpt.producerworkbench.service.ProjectPermissionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;
    private final ProjectMapper projectMapper;
    private final ProjectPermissionService projectPermissionService;

    @PostMapping
    public ResponseEntity<ApiResponse<ProjectResponse>> createProject(
            @Valid @RequestBody ProjectCreateRequest request,
            @AuthenticationPrincipal Jwt jwt,
            Authentication auth) {

        var permissions = projectPermissionService.checkProjectPermissions(auth, null);
        if (!permissions.isCanCreateProject()) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }

        String creatorEmail = jwt.getSubject();

        Project createdProject = projectService.createProject(request, creatorEmail);

        ProjectResponse projectResponse = projectMapper.toProjectResponse(createdProject);

        ApiResponse<ProjectResponse> response = ApiResponse.<ProjectResponse>builder()
                .message("Dự án đã được tạo thành công.")
                .result(projectResponse)
                .build();

        return ResponseEntity.ok(response);
    }
}