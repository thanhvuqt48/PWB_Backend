package com.fpt.producerworkbench.service.impl;

import com.fpt.producerworkbench.common.ContractStatus;
import com.fpt.producerworkbench.common.NotificationType;
import com.fpt.producerworkbench.common.ProjectRole;
import com.fpt.producerworkbench.common.RelatedEntityType;
import com.fpt.producerworkbench.dto.event.NotificationEvent;
import com.fpt.producerworkbench.dto.request.SendNotificationRequest;
import com.fpt.producerworkbench.dto.request.UpdateProjectMemberRoleRequest;
import com.fpt.producerworkbench.dto.response.PageResponse;
import com.fpt.producerworkbench.dto.response.ProjectMemberResponse;
import com.fpt.producerworkbench.dto.response.ProjectMembersViewResponse;
import com.fpt.producerworkbench.entity.Milestone;
import com.fpt.producerworkbench.entity.MilestoneMember;
import com.fpt.producerworkbench.entity.Project;
import com.fpt.producerworkbench.entity.ProjectMember;
import com.fpt.producerworkbench.entity.User;
import com.fpt.producerworkbench.exception.AppException;
import com.fpt.producerworkbench.exception.ErrorCode;
import com.fpt.producerworkbench.mapper.ProjectMemberMapper; // MỚI
import com.fpt.producerworkbench.repository.ContractRepository;
import com.fpt.producerworkbench.repository.MilestoneMemberRepository;
import com.fpt.producerworkbench.repository.MilestoneMoneySplitRepository;
import com.fpt.producerworkbench.repository.MilestoneRepository;
import com.fpt.producerworkbench.repository.ProjectMemberRepository;
import com.fpt.producerworkbench.repository.ProjectRepository;
import com.fpt.producerworkbench.repository.UserRepository;
import com.fpt.producerworkbench.service.NotificationService;
import com.fpt.producerworkbench.service.ProjectMemberService;
import com.fpt.producerworkbench.service.ProjectPermissionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProjectMemberServiceImpl implements ProjectMemberService {

    private final ProjectMemberRepository projectMemberRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final ProjectMemberMapper projectMemberMapper;
    private final ProjectPermissionService projectPermissionService;
    private final MilestoneRepository milestoneRepository;
    private final MilestoneMemberRepository milestoneMemberRepository;
    private final MilestoneMoneySplitRepository milestoneMoneySplitRepository;
    private final ContractRepository contractRepository;
    private final KafkaTemplate<String, NotificationEvent> kafkaTemplate;
    private final NotificationService notificationService;

    private static final String NOTIFICATION_TOPIC = "notification-delivery";

    @Override
    public List<ProjectMemberResponse> getProjectMembers(Long projectId) {
        List<ProjectMember> members = projectMemberRepository.findByProjectId(projectId);
        return members.stream().map(projectMemberMapper::toResponse).collect(Collectors.toList());
    }

    @Override
    public ProjectMembersViewResponse getProjectMembersForViewer(Long projectId, String viewerEmail,
            Pageable pageable) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new AppException(ErrorCode.PROJECT_NOT_FOUND));

        User viewer = userRepository.findByEmail(viewerEmail)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        List<ProjectMember> allMembers = projectMemberRepository.findByProjectId(projectId);

        boolean isAdmin = viewer.getRole() == com.fpt.producerworkbench.common.UserRole.ADMIN;
        boolean isOwner = project.getCreator().getId().equals(viewer.getId());

        boolean isMember = isAdmin || isOwner
                || allMembers.stream().anyMatch(m -> m.getUser().getId().equals(viewer.getId()));
        if (!isMember) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }

        if (isAdmin || isOwner) {
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

        boolean viewerIsAnonymousCollaborator = allMembers.stream()
                .anyMatch(m -> m.getUser().getId().equals(viewer.getId())
                        && m.getProjectRole() == ProjectRole.COLLABORATOR && m.isAnonymous());

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

    @Override
    @Transactional
    public void removeProjectMember(Long projectId, Long userId, Authentication auth) {
        if (userId == null || userId <= 0) {
            throw new AppException(ErrorCode.INVALID_PARAMETER_FORMAT);
        }

        var permissions = projectPermissionService.checkProjectPermissions(auth, projectId);
        boolean isOwner = permissions != null
                && permissions.getRole() != null
                && permissions.getRole().getProjectRole() == ProjectRole.OWNER;
        if (!isOwner) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }

        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new AppException(ErrorCode.PROJECT_NOT_FOUND));

        if (project.getCreator() != null && project.getCreator().getId().equals(userId)) {
            throw new AppException(ErrorCode.PROJECT_OWNER_CANNOT_BE_MODIFIED);
        }

        boolean isClientMember = project.getClient() != null && project.getClient().getId().equals(userId);

        if (isClientMember) {
            var contractOpt = contractRepository.findByProjectId(projectId);
            if (contractOpt.isPresent() && (ContractStatus.PAID.equals(contractOpt.get().getSignnowStatus())
                    || ContractStatus.COMPLETED.equals(contractOpt.get().getSignnowStatus()))) {
                throw new AppException(ErrorCode.PROJECT_CLIENT_CONTRACT_COMPLETED);
            }
            project.setClient(null);
        }

        ProjectMember member = projectMemberRepository.findByProjectIdAndUserId(projectId, userId)
                .orElseThrow(() -> new AppException(ErrorCode.PROJECT_MEMBER_NOT_FOUND));

        List<Milestone> projectMilestones = milestoneRepository.findByProjectIdOrderBySequenceAsc(projectId);
        if (!projectMilestones.isEmpty()) {
            List<Long> milestoneIds = projectMilestones.stream()
                    .map(Milestone::getId)
                    .collect(Collectors.toList());

            boolean hasMoneySplit = !milestoneMoneySplitRepository
                    .findByMilestoneIdInAndUserId(milestoneIds, userId)
                    .isEmpty();
            if (hasMoneySplit) {
                throw new AppException(ErrorCode.PROJECT_MEMBER_HAS_MONEY_SPLIT);
            }

            List<MilestoneMember> milestoneMemberships = milestoneMemberRepository
                    .findByMilestoneIdInAndUserId(milestoneIds, userId);
            if (!milestoneMemberships.isEmpty()) {
                milestoneMemberRepository.deleteAll(milestoneMemberships);
            }
        }

        User removedUser = member.getUser();
        projectMemberRepository.delete(member);
        sendProjectMemberRemovalEmail(project, removedUser);

        try {
            if (removedUser != null && removedUser.getId() != null) {
                String actionUrl = String.format("/projectDetail?id=%d", projectId);

                notificationService.sendNotification(
                        SendNotificationRequest.builder()
                                .userId(removedUser.getId())
                                .type(NotificationType.SYSTEM)
                                .title("Bạn đã bị xóa khỏi dự án")
                                .message(String.format("Bạn đã bị xóa khỏi dự án \"%s\".",
                                        project.getTitle()))
                                .relatedEntityType(RelatedEntityType.PROJECT)
                                .relatedEntityId(projectId)
                                .actionUrl(actionUrl)
                                .build());
            }
        } catch (Exception e) {
            log.error("Gặp lỗi khi gửi notification realtime cho người bị xóa khỏi dự án: {}", e.getMessage());
        }
    }

    @Override
    @Transactional
    public ProjectMemberResponse updateProjectMemberRole(Long projectId, Long userId,
            UpdateProjectMemberRoleRequest request, Authentication auth) {
        if (userId == null || userId <= 0) {
            throw new AppException(ErrorCode.INVALID_PARAMETER_FORMAT);
        }

        if (request == null || request.getProjectRole() == null) {
            throw new AppException(ErrorCode.INVALID_PARAMETER_FORMAT);
        }

        var permissions = projectPermissionService.checkProjectPermissions(auth, projectId);
        boolean isOwner = permissions != null
                && permissions.getRole() != null
                && permissions.getRole().getProjectRole() == ProjectRole.OWNER;
        if (!isOwner) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }

        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new AppException(ErrorCode.PROJECT_NOT_FOUND));

        if (project.getCreator() != null && project.getCreator().getId().equals(userId)) {
            throw new AppException(ErrorCode.PROJECT_OWNER_CANNOT_BE_MODIFIED);
        }

        if (project.getClient() != null && project.getClient().getId().equals(userId)) {
            throw new AppException(ErrorCode.PROJECT_CLIENT_CANNOT_BE_MODIFIED);
        }

        ProjectMember member = projectMemberRepository.findByProjectIdAndUserId(projectId, userId)
                .orElseThrow(() -> new AppException(ErrorCode.PROJECT_MEMBER_NOT_FOUND));

        ProjectRole newRole = request.getProjectRole();
        if (newRole == ProjectRole.OWNER) {
            throw new AppException(ErrorCode.PROJECT_OWNER_CANNOT_BE_MODIFIED);
        }
        if (newRole == ProjectRole.CLIENT) {
            throw new AppException(ErrorCode.PROJECT_CLIENT_CANNOT_BE_MODIFIED);
        }

        if (member.getProjectRole() == newRole) {
            return projectMemberMapper.toResponse(member);
        }

        member.setProjectRole(newRole);
        if (newRole != ProjectRole.COLLABORATOR && member.isAnonymous()) {
            member.setAnonymous(false);
        }

        ProjectMember saved = projectMemberRepository.save(member);
        return projectMemberMapper.toResponse(saved);
    }

    private void sendProjectMemberRemovalEmail(Project project, User removedUser) {
        if (removedUser == null) {
            return;
        }

        try {
            if (removedUser.getEmail() == null || removedUser.getEmail().isBlank()) {
                log.warn("Không thể gửi email thông báo xóa thành viên dự án vì user {} không có email",
                        removedUser.getId());
                return;
            }

            Map<String, Object> params = new HashMap<>();
            params.put("recipientName",
                    removedUser.getFullName() != null ? removedUser.getFullName() : removedUser.getEmail());
            params.put("projectName", project.getTitle());

            NotificationEvent event = NotificationEvent.builder()
                    .recipient(removedUser.getEmail())
                    .subject("Bạn đã bị xóa khỏi dự án: " + project.getTitle())
                    .templateCode("project-member-removed-template")
                    .param(params)
                    .build();

            kafkaTemplate.send(NOTIFICATION_TOPIC, event);
            log.info("Đã gửi email thông báo xóa thành viên dự án qua Kafka: userId={}, projectId={}",
                    removedUser.getId(), project.getId());
        } catch (Exception ex) {
            log.error("Lỗi khi gửi email thông báo xóa thành viên dự án qua Kafka: userId={}, projectId={}",
                    removedUser.getId(), project.getId(), ex);
        }
    }
}