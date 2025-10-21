package com.fpt.producerworkbench.service;

import com.fpt.producerworkbench.dto.response.ProjectDetailResponse;
import org.springframework.security.core.Authentication;

public interface ProjectDetailService {
    ProjectDetailResponse getProjectDetail(Authentication auth, Long projectId);
}
