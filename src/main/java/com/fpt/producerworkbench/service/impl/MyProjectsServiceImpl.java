package com.fpt.producerworkbench.service.impl;

import com.fpt.producerworkbench.common.ProjectStatus;
import com.fpt.producerworkbench.dto.response.ProjectSummaryResponse;
import com.fpt.producerworkbench.entity.User;
import com.fpt.producerworkbench.repository.ProjectRepository;
import com.fpt.producerworkbench.service.MyProjectsService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MyProjectsServiceImpl implements MyProjectsService {

    private final ProjectRepository projectRepository;

    @Override
    public Page<ProjectSummaryResponse> getMyProjects(User currentUser, String search, ProjectStatus status, Pageable pageable) {
        // Lấy projects mà user là owner
        Page<ProjectSummaryResponse> ownedProjects = projectRepository.findProjectSummariesByOwnerId(
                currentUser.getId(), search, status, pageable);
        
        // Lấy projects mà user là member
        Page<ProjectSummaryResponse> memberProjects = projectRepository.findProjectSummariesByMemberId(
                currentUser.getId(), search, status, pageable);
        
        // Merge và loại bỏ duplicate (nếu user vừa là owner vừa là member)
        List<ProjectSummaryResponse> allProjects = new ArrayList<>();
        allProjects.addAll(ownedProjects.getContent());
        
        // Chỉ thêm member projects không trùng với owned projects
        List<Long> ownedProjectIds = ownedProjects.getContent().stream()
                .map(ProjectSummaryResponse::getId)
                .collect(Collectors.toList());
        
        List<ProjectSummaryResponse> uniqueMemberProjects = memberProjects.getContent().stream()
                .filter(project -> !ownedProjectIds.contains(project.getId()))
                .collect(Collectors.toList());
        
        allProjects.addAll(uniqueMemberProjects);
        
        // Sort theo updatedAt desc (giả định pageable đã có sort)
        allProjects.sort((p1, p2) -> p2.getCreatedAt().compareTo(p1.getCreatedAt()));
        
        // Tạo Page mới với tất cả projects
        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), allProjects.size());
        List<ProjectSummaryResponse> pageContent = allProjects.subList(start, end);
        
        return new PageImpl<>(pageContent, pageable, allProjects.size());
    }
}