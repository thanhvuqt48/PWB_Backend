package com.fpt.producerworkbench.service;

import com.fpt.producerworkbench.dto.request.ProjectCreateRequest;
import com.fpt.producerworkbench.entity.Project;
import org.springframework.security.core.Authentication;

public interface ProjectService {
    Project createProject(ProjectCreateRequest request, String creatorEmail);

    Project completeProject(Long projectId, Authentication auth);
}