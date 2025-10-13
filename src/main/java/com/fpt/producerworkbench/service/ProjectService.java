package com.fpt.producerworkbench.service;

import com.fpt.producerworkbench.dto.request.ProjectCreateRequest;
import com.fpt.producerworkbench.entity.Project;

public interface ProjectService {
    Project createProject(ProjectCreateRequest request, String creatorEmail);
}