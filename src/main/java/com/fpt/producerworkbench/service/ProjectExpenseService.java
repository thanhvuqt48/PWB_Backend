package com.fpt.producerworkbench.service;

import com.fpt.producerworkbench.dto.response.ProjectExpenseChartResponse;
import com.fpt.producerworkbench.dto.response.ProjectExpenseDetailResponse;
import com.fpt.producerworkbench.dto.response.ProjectMoneySplitDetailResponse;
import org.springframework.security.core.Authentication;

import java.util.List;

public interface ProjectExpenseService {
    ProjectExpenseChartResponse getProjectExpenseChart(Long projectId, Authentication auth);
    
    List<ProjectExpenseDetailResponse> getProjectExpenseDetails(Long projectId, Authentication auth);
    
    List<ProjectMoneySplitDetailResponse> getProjectMoneySplitDetails(Long projectId, Authentication auth);
}

