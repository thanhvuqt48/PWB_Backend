package com.fpt.producerworkbench.controller;

import com.fpt.producerworkbench.dto.response.ApiResponse;
import com.fpt.producerworkbench.dto.response.ProjectPermissionResponse;
import com.fpt.producerworkbench.service.ProjectPermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
public class ProjectPermissionController {

    private final ProjectPermissionService projectPermissionService;

    @GetMapping("/permissions")
    public ApiResponse<ProjectPermissionResponse> getProjectPermissions(
            Authentication auth) {
        var permissions = projectPermissionService.checkProjectPermissions(auth, null);
        return ApiResponse.<ProjectPermissionResponse>builder()
                .code(200)
                .result(permissions)
                .build();
    }

    @GetMapping("/{projectId}/permissions")
    public ApiResponse<ProjectPermissionResponse> getProjectPermissions(
            @PathVariable Long projectId,
            Authentication auth) {
        var permissions = projectPermissionService.checkProjectPermissions(auth, projectId);
        return ApiResponse.<ProjectPermissionResponse>builder()
                .code(200)
                .result(permissions)
                .build();
    }
}
