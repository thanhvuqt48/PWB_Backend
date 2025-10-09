package com.fpt.producerworkbench.service.impl;

import com.fpt.producerworkbench.dto.response.ProjectMemberResponse;
import com.fpt.producerworkbench.entity.ProjectMember;
import com.fpt.producerworkbench.mapper.ProjectMemberMapper; // MỚI
import com.fpt.producerworkbench.repository.ProjectMemberRepository;
import com.fpt.producerworkbench.service.ProjectMemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProjectMemberServiceImpl implements ProjectMemberService {

    private final ProjectMemberRepository projectMemberRepository;
    private final ProjectMemberMapper projectMemberMapper;

    @Override
    public List<ProjectMemberResponse> getProjectMembers(Long projectId) {
        List<ProjectMember> members = projectMemberRepository.findByProjectId(projectId);
        return members.stream().map(projectMemberMapper::toResponse).collect(Collectors.toList());
    }

}