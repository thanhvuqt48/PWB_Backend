package com.fpt.producerworkbench.controller;

import com.fpt.producerworkbench.dto.response.ApiResponse;
import com.fpt.producerworkbench.dto.response.ProjectDetailResponse;
import com.fpt.producerworkbench.service.ProjectDetailService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
public class ProjectDetailController {

    private final ProjectDetailService projectDetailService;

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

