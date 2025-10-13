package com.fpt.producerworkbench.service;

import com.fpt.producerworkbench.common.ProjectStatus;
import com.fpt.producerworkbench.dto.response.ProjectSummaryResponse;
import com.fpt.producerworkbench.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface MyProjectsService {

    Page<ProjectSummaryResponse> getMyProjects(User currentUser, String search, ProjectStatus status, Pageable pageable);
}