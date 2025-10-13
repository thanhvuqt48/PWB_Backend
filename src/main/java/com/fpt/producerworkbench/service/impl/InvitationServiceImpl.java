package com.fpt.producerworkbench.service.impl;

import com.fpt.producerworkbench.common.InvitationStatus;
import com.fpt.producerworkbench.common.ProjectRole;
import com.fpt.producerworkbench.dto.event.NotificationEvent;
import com.fpt.producerworkbench.dto.response.PageResponse;
import com.fpt.producerworkbench.dto.request.InvitationRequest;
import com.fpt.producerworkbench.dto.response.InvitationResponse;
import com.fpt.producerworkbench.entity.*;
import com.fpt.producerworkbench.exception.AppException;
import com.fpt.producerworkbench.exception.ErrorCode;
import com.fpt.producerworkbench.mapper.InvitationMapper;
import com.fpt.producerworkbench.repository.*;
import com.fpt.producerworkbench.service.InvitationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class InvitationServiceImpl implements InvitationService {

    private final ProjectRepository projectRepository;
    private final ProjectInvitationRepository invitationRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final KafkaTemplate<String, NotificationEvent> kafkaTemplate;
    private final InvitationMapper invitationMapper;

    private static final String NOTIFICATION_TOPIC = "notification-delivery";

    @Override
    @Transactional
    public String createInvitation(Long projectId, InvitationRequest request, User inviter) {
        log.info("Bắt đầu tạo lời mời cho dự án {} bởi {}", projectId, inviter.getEmail());

        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new AppException(ErrorCode.PROJECT_NOT_FOUND));

        if (!project.getCreator().getId().equals(inviter.getId())) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }

        if (request.getRole() == ProjectRole.CLIENT && project.getClient() != null) {
            throw new AppException(ErrorCode.CLIENT_ALREADY_EXISTS);
        }

        if (inviter.getEmail().equalsIgnoreCase(request.getEmail())) {
            throw new AppException(ErrorCode.INVITATION_SELF_NOT_ALLOWED);
        }

        // Chặn mời nếu email đã là thành viên dự án (owner/client/member bất kỳ)
        boolean alreadyMember = projectMemberRepository.findByProjectIdAndUserEmail(projectId, request.getEmail()).isPresent()
                || (project.getClient() != null && project.getClient().getEmail().equalsIgnoreCase(request.getEmail()))
                || project.getCreator().getEmail().equalsIgnoreCase(request.getEmail());
        if (alreadyMember) {
            throw new AppException(ErrorCode.USER_ALREADY_MEMBER);
        }

        // Vô hiệu hóa tất cả lời mời PENDING cũ của email này trong project (đánh EXPIRED)
        try {
            invitationRepository.expirePendingInvitationsForEmail(projectId, request.getEmail());
        } catch (Exception ex) {
            log.warn("Không thể expire lời mời cũ cho {} trong project {}", request.getEmail(), projectId, ex);
        }

        String token = UUID.randomUUID().toString();
        boolean collaboratorAnonymous = Boolean.TRUE.equals(request.getAnonymous()) && request.getRole() == ProjectRole.COLLABORATOR;

        ProjectInvitation invitation = ProjectInvitation.builder()
                .project(project)
                .email(request.getEmail())
                .invitedRole(request.getRole())
                .token(token)
                .status(InvitationStatus.PENDING)
                .expiresAt(LocalDateTime.now().plusDays(2))
                .collaboratorAnonymous(collaboratorAnonymous)
                .build();

        ProjectInvitation savedInvitation = invitationRepository.save(invitation);
        log.info("Đã lưu lời mời vào DB thành công. ID: {}", savedInvitation.getId());

        String invitationLink = "http://localhost:5173/accept-invitation?token=" + savedInvitation.getToken();

        try {
            NotificationEvent event = NotificationEvent.builder()
                    .recipient(savedInvitation.getEmail())
                    .subject("Lời mời tham gia dự án: " + project.getTitle())
                    .templateCode("invitation-email-template")
                    .param(Map.of(
                            "inviterName", inviter.getFullName(),
                            "projectName", project.getTitle(),
                            "role", savedInvitation.getInvitedRole().name(),
                            "invitationLink", invitationLink
                    ))
                    .build();
            kafkaTemplate.send(NOTIFICATION_TOPIC, event);
            log.info("Đã gửi message mời tới Kafka thành công!");
        } catch (Exception e) {
            log.error("Gặp lỗi khi gửi message mời tới Kafka!", e);
        }

        return invitationLink;
    }

    @Override
    @Transactional
    public void acceptInvitation(String token, User acceptingUser) {
        ProjectInvitation invitation = invitationRepository.findByToken(token)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND));

        validateInvitation(invitation, acceptingUser);

        Project project = invitation.getProject();
        User projectOwner = project.getCreator();

        if (invitation.getInvitedRole() == ProjectRole.CLIENT) {
            projectRepository.findById(project.getId()).ifPresent(p -> {
                if (p.getClient() != null) {
                    throw new AppException(ErrorCode.CLIENT_EXISTED);
                }
            });
        }

        boolean anonymousFlag = Boolean.TRUE.equals(invitation.getCollaboratorAnonymous()) && invitation.getInvitedRole() == ProjectRole.COLLABORATOR;

        ProjectMember newMember = ProjectMember.builder()
                .project(project)
                .user(acceptingUser)
                .projectRole(invitation.getInvitedRole())
                .anonymous(anonymousFlag)
                .build();
        projectMemberRepository.save(newMember);

        if (invitation.getInvitedRole() == ProjectRole.CLIENT) {
            project.setClient(acceptingUser);
            projectRepository.save(project);
        }

        invitation.setStatus(InvitationStatus.ACCEPTED);
        invitationRepository.save(invitation);

        NotificationEvent confirmationEvent = NotificationEvent.builder()
                .recipient(acceptingUser.getEmail())
                .subject("Chào mừng bạn đến với dự án: " + project.getTitle())
                .templateCode("invitation-accepted-confirmation-template")
                .param(Map.of(
                        "userName", acceptingUser.getFullName(),
                        "projectName", project.getTitle(),
                        "role", invitation.getInvitedRole().name()
                ))
                .build();
        kafkaTemplate.send(NOTIFICATION_TOPIC, confirmationEvent);

        NotificationEvent ownerNotificationEvent = NotificationEvent.builder()
                .recipient(projectOwner.getEmail())
                .subject("Thành viên mới đã tham gia dự án của bạn")
                .templateCode("owner-notification-new-member-template")
                .param(Map.of(
                        "ownerName", projectOwner.getFullName(),
                        "newMemberName", acceptingUser.getFullName(),
                        "newMemberEmail", acceptingUser.getEmail(),
                        "projectName", project.getTitle(),
                        "role", invitation.getInvitedRole().name()
                ))
                .build();
        kafkaTemplate.send(NOTIFICATION_TOPIC, ownerNotificationEvent);

        log.info("Thành công: {} đã tham gia dự án '{}' với vai trò {}", acceptingUser.getEmail(), project.getTitle(), invitation.getInvitedRole());
    }

    @Override
    @Transactional
    public void acceptInvitationById(Long invitationId, User acceptingUser) {
        ProjectInvitation invitation = invitationRepository.findById(invitationId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND));

        // Reuse existing validation logic
        validateInvitation(invitation, acceptingUser);

        Project project = invitation.getProject();
        User projectOwner = project.getCreator();

        if (invitation.getInvitedRole() == ProjectRole.CLIENT) {
            projectRepository.findById(project.getId()).ifPresent(p -> {
                if (p.getClient() != null) {
                    throw new AppException(ErrorCode.CLIENT_EXISTED);
                }
            });
        }

        boolean anonymousFlag = Boolean.TRUE.equals(invitation.getCollaboratorAnonymous()) && invitation.getInvitedRole() == ProjectRole.COLLABORATOR;

        ProjectMember newMember = ProjectMember.builder()
                .project(project)
                .user(acceptingUser)
                .projectRole(invitation.getInvitedRole())
                .anonymous(anonymousFlag)
                .build();
        projectMemberRepository.save(newMember);

        if (invitation.getInvitedRole() == ProjectRole.CLIENT) {
            project.setClient(acceptingUser);
            projectRepository.save(project);
        }

        invitation.setStatus(InvitationStatus.ACCEPTED);
        invitationRepository.save(invitation);

        NotificationEvent confirmationEvent = NotificationEvent.builder()
                .recipient(acceptingUser.getEmail())
                .subject("Chào mừng bạn đến với dự án: " + project.getTitle())
                .templateCode("invitation-accepted-confirmation-template")
                .param(Map.of(
                        "userName", acceptingUser.getFullName(),
                        "projectName", project.getTitle(),
                        "role", invitation.getInvitedRole().name()
                ))
                .build();
        kafkaTemplate.send(NOTIFICATION_TOPIC, confirmationEvent);

        NotificationEvent ownerNotificationEvent = NotificationEvent.builder()
                .recipient(projectOwner.getEmail())
                .subject("Thành viên mới đã tham gia dự án của bạn")
                .templateCode("owner-notification-new-member-template")
                .param(Map.of(
                        "ownerName", projectOwner.getFullName(),
                        "newMemberName", acceptingUser.getFullName(),
                        "newMemberEmail", acceptingUser.getEmail(),
                        "projectName", project.getTitle(),
                        "role", invitation.getInvitedRole().name()
                ))
                .build();
        kafkaTemplate.send(NOTIFICATION_TOPIC, ownerNotificationEvent);
    }

    @Override
    public List<InvitationResponse> getPendingInvitationsForProject(Long projectId, User currentUser) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND));

        if (!project.getCreator().getId().equals(currentUser.getId())) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }

        List<ProjectInvitation> invitations = invitationRepository.findByProjectIdAndStatus(projectId, InvitationStatus.PENDING)
                .stream()
                .filter(inv -> inv.getExpiresAt().isAfter(LocalDateTime.now()))
                .collect(Collectors.toList());
        return invitations.stream().map(invitationMapper::toOwnerInvitationResponse).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void cancelInvitation(Long invitationId, User currentUser) {
        ProjectInvitation invitation = invitationRepository.findById(invitationId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND));

        Project project = invitation.getProject();
        if (!project.getCreator().getId().equals(currentUser.getId())) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }

        if (invitation.getStatus() != InvitationStatus.PENDING) {
            throw new AppException(ErrorCode.INVITATION_NOT_CANCELABLE);
        }

        invitation.setStatus(InvitationStatus.CANCELLED);
        invitationRepository.save(invitation);

        NotificationEvent event = NotificationEvent.builder()
                .recipient(invitation.getEmail())
                .subject("Lời mời tham gia dự án \"" + project.getTitle() + "\" đã được hủy")
                .templateCode("invitation-cancelled-template")
                .param(Map.of("projectName", project.getTitle()))
                .build();
        kafkaTemplate.send(NOTIFICATION_TOPIC, event);
    }

    @Override
    public List<InvitationResponse> getMyPendingInvitations(User currentUser) {
        List<ProjectInvitation> invitations = invitationRepository.findByEmailAndStatus(currentUser.getEmail(), InvitationStatus.PENDING);
        return invitations.stream().map(invitationMapper::toInviteeInvitationResponse).collect(Collectors.toList());
    }

    @Override
    public PageResponse<InvitationResponse> getMyPendingInvitationsPage(User currentUser, Pageable pageable) {
        Page<ProjectInvitation> page = invitationRepository.findByEmailAndStatusAndExpiresAtAfter(
                currentUser.getEmail(), InvitationStatus.PENDING, LocalDateTime.now(), pageable);
        Page<InvitationResponse> mapped = page.map(invitationMapper::toInviteeInvitationResponse);
        return PageResponse.fromPage(mapped);
    }

    @Override
    @Transactional
    public void declineInvitation(Long invitationId, User currentUser) {
        ProjectInvitation invitation = invitationRepository.findById(invitationId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND));

        if (!invitation.getEmail().equalsIgnoreCase(currentUser.getEmail())) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }

        if (invitation.getStatus() != InvitationStatus.PENDING) {
            throw new AppException(ErrorCode.INVITATION_NOT_REJECTABLE);
        }

        invitation.setStatus(InvitationStatus.DECLINED);
        invitationRepository.save(invitation);

        Project project = invitation.getProject();
        User owner = project.getCreator();
        NotificationEvent event = NotificationEvent.builder()
                .recipient(owner.getEmail())
                .subject("Thông báo: Lời mời tham gia dự án đã bị từ chối")
                .templateCode("invitation-declined-template")
                .param(Map.of(
                        "projectName", project.getTitle(),
                        "inviteeEmail", currentUser.getEmail(),
                        "inviteeName", currentUser.getFullName()
                ))
                .build();
        kafkaTemplate.send(NOTIFICATION_TOPIC, event);
    }

    @Override
    public PageResponse<InvitationResponse> getAllMyInvitations(User currentUser, InvitationStatus status, Pageable pageable) {
        Page<ProjectInvitation> page = (status == null)
                ? invitationRepository.findByEmail(currentUser.getEmail(), pageable)
                : invitationRepository.findByEmailAndStatus(currentUser.getEmail(), status, pageable);
        Page<InvitationResponse> mapped = page.map(invitationMapper::toInviteeInvitationResponse);
        return PageResponse.fromPage(mapped);
    }

    @Override
    public PageResponse<InvitationResponse> getAllOwnedInvitations(User currentUser, InvitationStatus status, Pageable pageable) {
        // Only owner (PRODUCER/ADMIN) should call controller; here we just fetch by creator email
        Page<ProjectInvitation> page = (status == null)
                ? invitationRepository.findByProjectCreatorEmail(currentUser.getEmail(), pageable)
                : invitationRepository.findByProjectCreatorEmailAndStatus(currentUser.getEmail(), status, pageable);
        Page<InvitationResponse> mapped = page.map(invitationMapper::toOwnerInvitationResponse);
        return PageResponse.fromPage(mapped);
    }

    private void validateInvitation(ProjectInvitation invitation, User acceptingUser) {
        if (invitation.getStatus() != InvitationStatus.PENDING) {
            throw new AppException(ErrorCode.INVITATION_EXPIRED_OR_NOT_FOUND);
        }
        if (invitation.getExpiresAt().isBefore(LocalDateTime.now())) {
            invitation.setStatus(InvitationStatus.EXPIRED);
            invitationRepository.save(invitation);
            throw new AppException(ErrorCode.INVITATION_EXPIRED);
        }
        if (!invitation.getEmail().equalsIgnoreCase(acceptingUser.getEmail())) {
            throw new AppException(ErrorCode.INVITATION_NOT_ACCEPTED);
        }
    }

    // Dọn dẹp định kỳ: chuyển các lời mời PENDING đã hết hạn sang EXPIRED
    @Scheduled(cron = "0 */10 * * * *") // mỗi 10 phút
    @Transactional
    public void expirePastDueInvitations() {
        int affected = invitationRepository.expireAllPastDuePendingInvitations();
        if (affected > 0) {
            log.info("Expired {} pending invitations that passed expiration.", affected);
        }
    }
}