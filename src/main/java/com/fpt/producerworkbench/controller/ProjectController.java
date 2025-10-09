package com.fpt.producerworkbench.controller;

import com.fpt.producerworkbench.dto.request.ProjectCreateRequest;
import com.fpt.producerworkbench.dto.response.ApiResponse;
import com.fpt.producerworkbench.dto.response.ProjectResponse;
import com.fpt.producerworkbench.entity.Project;
import com.fpt.producerworkbench.entity.User;
import com.fpt.producerworkbench.mapper.ProjectMapper;
import com.fpt.producerworkbench.service.ProjectService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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

    @PostMapping
    @PreAuthorize("hasAnyAuthority('PRODUCER', 'ADMIN')")
    public ResponseEntity<ApiResponse<ProjectResponse>> createProject(
            @Valid @RequestBody ProjectCreateRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        String creatorEmail = jwt.getSubject();

        Project createdProject = projectService.createProject(request, creatorEmail);

        ProjectResponse projectResponse = projectMapper.toProjectResponse(createdProject);

        ApiResponse<ProjectResponse> response = ApiResponse.<ProjectResponse>builder()
                .message("Project created successfully.")
                .result(projectResponse)
                .build();

        return ResponseEntity.ok(response);
    }
}