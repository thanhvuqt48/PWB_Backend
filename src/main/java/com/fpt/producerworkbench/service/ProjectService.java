package com.fpt.producerworkbench.service;

import com.fpt.producerworkbench.dto.request.ProjectCreateRequest;
import com.fpt.producerworkbench.entity.Project;
import com.fpt.producerworkbench.entity.User;

public interface ProjectService {
    Project createProject(ProjectCreateRequest request, String creatorEmail);
}