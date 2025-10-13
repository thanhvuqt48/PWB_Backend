package com.fpt.producerworkbench.service.impl;

import com.fpt.producerworkbench.common.ProjectStatus;
import com.fpt.producerworkbench.dto.response.ProjectSummaryResponse;
import com.fpt.producerworkbench.entity.User;
import com.fpt.producerworkbench.repository.ProjectRepository;
import com.fpt.producerworkbench.service.MyProjectsService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MyProjectsServiceImpl implements MyProjectsService {

    private final ProjectRepository projectRepository;

    @Override
    public Page<ProjectSummaryResponse> getMyProjects(User currentUser, String search, ProjectStatus status, Pageable pageable) {
        return projectRepository.findProjectSummariesByMemberId(currentUser.getId(), search, status, pageable);
    }
}