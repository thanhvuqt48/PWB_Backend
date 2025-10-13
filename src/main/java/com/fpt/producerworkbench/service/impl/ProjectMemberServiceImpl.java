package com.fpt.producerworkbench.service.impl;

import com.fpt.producerworkbench.common.ProjectRole;
import com.fpt.producerworkbench.dto.response.ProjectMemberResponse;
import com.fpt.producerworkbench.dto.response.ProjectMembersViewResponse;
import com.fpt.producerworkbench.dto.response.PageResponse;
import com.fpt.producerworkbench.entity.Project;
import com.fpt.producerworkbench.entity.ProjectMember;
import com.fpt.producerworkbench.entity.User;
import com.fpt.producerworkbench.exception.AppException;
import com.fpt.producerworkbench.exception.ErrorCode;
import com.fpt.producerworkbench.mapper.ProjectMemberMapper; // MỚI
import com.fpt.producerworkbench.repository.ProjectMemberRepository;
import com.fpt.producerworkbench.repository.ProjectRepository;
import com.fpt.producerworkbench.repository.UserRepository;
import com.fpt.producerworkbench.service.ProjectMemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProjectMemberServiceImpl implements ProjectMemberService {

    private final ProjectMemberRepository projectMemberRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final ProjectMemberMapper projectMemberMapper;

    @Override
    public List<ProjectMemberResponse> getProjectMembers(Long projectId) {
        List<ProjectMember> members = projectMemberRepository.findByProjectId(projectId);
        return members.stream().map(projectMemberMapper::toResponse).collect(Collectors.toList());
    }

    @Override
    public ProjectMembersViewResponse getProjectMembersForViewer(Long projectId, String viewerEmail, Pageable pageable) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new AppException(ErrorCode.PROJECT_NOT_FOUND));

        User viewer = userRepository.findByEmail(viewerEmail)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        List<ProjectMember> allMembers = projectMemberRepository.findByProjectId(projectId);

        boolean isOwner = project.getCreator().getId().equals(viewer.getId());

        boolean isMember = isOwner || allMembers.stream().anyMatch(m -> m.getUser().getId().equals(viewer.getId()));
        if (!isMember) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }

        if (isOwner) {
            Page<ProjectMember> pageData = projectMemberRepository.findByProjectId(projectId, pageable);
            Page<ProjectMemberResponse> mappedPage = pageData.map(projectMemberMapper::toResponse);
            int anonCount = (int) allMembers.stream()
                    .filter(m -> m.getProjectRole() == ProjectRole.COLLABORATOR && m.isAnonymous())
                    .count();
            return ProjectMembersViewResponse.builder()
                    .members(PageResponse.fromPage(mappedPage))
                    .anonymousCollaboratorCount(anonCount)
                    .anonymousSummaryMessage(null)
                    .build();
        }

        // Non-owner: hide anonymous collaborators and hide client info for anonymous collaborators viewing
        boolean viewerIsAnonymousCollaborator = allMembers.stream().anyMatch(m ->
                m.getUser().getId().equals(viewer.getId()) && m.getProjectRole() == ProjectRole.COLLABORATOR && m.isAnonymous());

        Page<ProjectMember> pageData;
        if (viewerIsAnonymousCollaborator) {
            pageData = projectMemberRepository.findVisibleForAnonymousCollaborator(projectId, pageable);
        } else {
            pageData = projectMemberRepository.findVisibleForNonOwner(projectId, pageable);
        }
        Page<ProjectMemberResponse> mappedPage = pageData.map(projectMemberMapper::toResponse);

        int anonCount = (int) allMembers.stream()
                .filter(m -> m.getProjectRole() == ProjectRole.COLLABORATOR && m.isAnonymous())
                .count();

        String summary = anonCount > 0 ? String.format("... và %d cộng tác viên ẩn danh.", anonCount) : null;

        return ProjectMembersViewResponse.builder()
                .members(PageResponse.fromPage(mappedPage))
                .anonymousCollaboratorCount(anonCount)
                .anonymousSummaryMessage(summary)
                .build();
    }
}