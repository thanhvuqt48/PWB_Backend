package com.fpt.producerworkbench.service;

import com.fpt.producerworkbench.dto.response.ProjectPermissionResponse;
import org.springframework.security.core.Authentication;

public interface ProjectPermissionService {
    ProjectPermissionResponse checkProjectPermissions(Authentication auth, Long projectId);
}
