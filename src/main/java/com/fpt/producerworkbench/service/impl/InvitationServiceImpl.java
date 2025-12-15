package com.fpt.producerworkbench.service.impl;

import com.fpt.producerworkbench.common.InvitationStatus;
import com.fpt.producerworkbench.common.NotificationType;
import com.fpt.producerworkbench.common.ProjectRole;
import com.fpt.producerworkbench.common.RelatedEntityType;
import com.fpt.producerworkbench.dto.event.NotificationEvent;
import com.fpt.producerworkbench.dto.response.PageResponse;
import com.fpt.producerworkbench.dto.request.InvitationRequest;
import com.fpt.producerworkbench.dto.request.SendNotificationRequest;
import com.fpt.producerworkbench.dto.response.FollowResponse;
import com.fpt.producerworkbench.dto.response.InvitationResponse;
import com.fpt.producerworkbench.dto.response.InvitationSuggestionResponse;
import com.fpt.producerworkbench.entity.*;
import com.fpt.producerworkbench.exception.AppException;
import com.fpt.producerworkbench.exception.ErrorCode;
import com.fpt.producerworkbench.mapper.InvitationMapper;
import com.fpt.producerworkbench.repository.*;
import com.fpt.producerworkbench.service.FollowService;
import com.fpt.producerworkbench.service.InvitationService;
import com.fpt.producerworkbench.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.LocalDateTime;
import java.util.*;
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
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final FollowService followService;

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

        boolean alreadyMember = projectMemberRepository.findByProjectIdAndUserEmail(projectId, request.getEmail())
                .isPresent()
                || (project.getClient() != null && project.getClient().getEmail().equalsIgnoreCase(request.getEmail()))
                || project.getCreator().getEmail().equalsIgnoreCase(request.getEmail());
        if (alreadyMember) {
            throw new AppException(ErrorCode.USER_ALREADY_MEMBER);
        }

        // Vô hiệu hóa tất cả lời mời PENDING cũ của email này trong project (đánh
        // EXPIRED)
        try {
            invitationRepository.expirePendingInvitationsForEmail(projectId, request.getEmail());
        } catch (Exception ex) {
            log.warn("Không thể expire lời mời cũ cho {} trong project {}", request.getEmail(), projectId, ex);
        }

        String token = UUID.randomUUID().toString();
        boolean collaboratorAnonymous = Boolean.TRUE.equals(request.getAnonymous())
                && request.getRole() == ProjectRole.COLLABORATOR;

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

        String invitationLink = "/accept-invitation?token=" + savedInvitation.getToken();

        try {
            NotificationEvent event = NotificationEvent.builder()
                    .recipient(savedInvitation.getEmail())
                    .subject("Lời mời tham gia dự án: " + project.getTitle())
                    .templateCode("invitation-email-template")
                    .param(Map.of(
                            "inviterName", inviter.getFullName(),
                            "projectName", project.getTitle(),
                            "role", savedInvitation.getInvitedRole().name(),
                            "invitationLink", invitationLink))
                    .build();
            kafkaTemplate.send(NOTIFICATION_TOPIC, event);
            log.info("Đã gửi message mời tới Kafka thành công!");
        } catch (Exception e) {
            log.error("Gặp lỗi khi gửi message mời tới Kafka!", e);
        }

        try {
            userRepository.findByEmail(savedInvitation.getEmail()).ifPresent(user -> {
                String roleName = savedInvitation.getInvitedRole() == ProjectRole.COLLABORATOR ? "Cộng tác viên"
                        : savedInvitation.getInvitedRole() == ProjectRole.OBSERVER ? "Người quan sát"
                        : "Khách hàng";
                notificationService.sendNotification(
                        SendNotificationRequest.builder()
                                .userId(user.getId())
                                .type(NotificationType.PROJECT_INVITATION)
                                .title("Lời mời tham gia dự án")
                                .message(String.format("%s đã mời bạn tham gia dự án \"%s\" với vai trò %s",
                                        inviter.getFullName() != null ? inviter.getFullName() : inviter.getEmail(),
                                        project.getTitle(),
                                        roleName))
                                .relatedEntityType(RelatedEntityType.PROJECT)
                                .relatedEntityId(projectId)
                                .actionUrl("/myInvitations")
                                .build());
            });
        } catch (Exception e) {
            log.error("Gặp lỗi khi gửi notification realtime: {}", e.getMessage());
        }

        return invitationLink;
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

        boolean anonymousFlag = Boolean.TRUE.equals(invitation.getCollaboratorAnonymous())
                && invitation.getInvitedRole() == ProjectRole.COLLABORATOR;

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
                        "role", invitation.getInvitedRole().name()))
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
                        "role", invitation.getInvitedRole().name()))
                .build();
        kafkaTemplate.send(NOTIFICATION_TOPIC, ownerNotificationEvent);

        try {
            String roleName = invitation.getInvitedRole() == ProjectRole.COLLABORATOR ? "Cộng tác viên"
                    : invitation.getInvitedRole() == ProjectRole.OBSERVER ? "Người quan sát"
                    : "Khách hàng";
            String actionUrl = String.format("/teamInvitation?id=%d", project.getId());

            notificationService.sendNotification(
                    SendNotificationRequest.builder()
                            .userId(projectOwner.getId())
                            .type(NotificationType.PROJECT_INVITATION)
                            .title("Thành viên mới đã tham gia dự án")
                            .message(String.format("%s đã chấp nhận lời mời và tham gia dự án \"%s\" với vai trò %s.",
                                    acceptingUser.getFullName() != null ? acceptingUser.getFullName() : acceptingUser.getEmail(),
                                    project.getTitle(),
                                    roleName))
                            .relatedEntityType(RelatedEntityType.PROJECT)
                            .relatedEntityId(project.getId())
                            .actionUrl(actionUrl)
                            .build());
        } catch (Exception e) {
            log.error("Gặp lỗi khi gửi notification realtime cho owner khi accept invitation: {}", e.getMessage());
        }
    }

    @Override
    public List<InvitationResponse> getPendingInvitationsForProject(Long projectId, User currentUser) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND));

        if (!project.getCreator().getId().equals(currentUser.getId())) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }

        List<ProjectInvitation> invitations = invitationRepository
                .findByProjectIdAndStatus(projectId, InvitationStatus.PENDING)
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

        try {
            userRepository.findByEmail(invitation.getEmail()).ifPresent(user -> {
                String actionUrl = "/myInvitations";

                notificationService.sendNotification(
                        SendNotificationRequest.builder()
                                .userId(user.getId())
                                .type(NotificationType.PROJECT_INVITATION)
                                .title("Lời mời đã được hủy")
                                .message(String.format("Lời mời tham gia dự án \"%s\" đã được hủy bởi chủ dự án.",
                                        project.getTitle()))
                                .relatedEntityType(RelatedEntityType.PROJECT)
                                .relatedEntityId(project.getId())
                                .actionUrl(actionUrl)
                                .build());
            });
        } catch (Exception e) {
            log.error("Gặp lỗi khi gửi notification realtime khi cancel invitation: {}", e.getMessage());
        }
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
                        "inviteeName", currentUser.getFullName()))
                .build();
        kafkaTemplate.send(NOTIFICATION_TOPIC, event);

        try {
            String actionUrl = String.format("/projectDetail?id=%d", project.getId());

            notificationService.sendNotification(
                    SendNotificationRequest.builder()
                            .userId(owner.getId())
                            .type(NotificationType.PROJECT_INVITATION)
                            .title("Lời mời đã bị từ chối")
                            .message(String.format("%s đã từ chối lời mời tham gia dự án \"%s\".",
                                    currentUser.getFullName() != null ? currentUser.getFullName() : currentUser.getEmail(),
                                    project.getTitle()))
                            .relatedEntityType(RelatedEntityType.PROJECT)
                            .relatedEntityId(project.getId())
                            .actionUrl(actionUrl)
                            .build());
        } catch (Exception e) {
            log.error("Gặp lỗi khi gửi notification realtime cho owner khi decline invitation: {}", e.getMessage());
        }
    }

    @Override
    public PageResponse<InvitationResponse> getAllOwnedInvitations(User currentUser, InvitationStatus status,
                                                                   Pageable pageable) {
        // Only owner (PRODUCER/ADMIN) should call controller; here we just fetch by
        // creator email
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

    @Override
    @Transactional(readOnly = true)
    public PageResponse<InvitationSuggestionResponse> getSuggestedUsersForInvitation(Long projectId, User currentUser, Pageable pageable) {
        // 1. Kiểm tra quyền - chỉ owner mới có thể xem suggestions
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new AppException(ErrorCode.PROJECT_NOT_FOUND));

        if (!project.getCreator().getId().equals(currentUser.getId())) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }

        // 2. Lấy danh sách người đã follow
        Page<FollowResponse> followingPage = followService.getFollowing(currentUser.getId(), pageable);

        if (followingPage.isEmpty()) {
            return PageResponse.fromPage(Page.empty(pageable));
        }

        // 3. Lấy danh sách userIds đã là thành viên của project
        List<ProjectMember> existingMembers = projectMemberRepository.findByProjectId(projectId);
        Set<Long> existingUserIds = existingMembers.stream()
                .map(member -> member.getUser().getId())
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // Thêm creator và client vào danh sách đã có
        if (project.getCreator() != null && project.getCreator().getId() != null) {
            existingUserIds.add(project.getCreator().getId());
        }
        if (project.getClient() != null && project.getClient().getId() != null) {
            existingUserIds.add(project.getClient().getId());
        }

        // 4. Lọc và map sang InvitationSuggestionResponse
        List<InvitationSuggestionResponse> suggestions = followingPage.getContent().stream()
                .filter(followResponse -> !existingUserIds.contains(followResponse.getId()))
                .map(followResponse -> {
                    // Lấy thông tin đầy đủ từ User để có email
                    User user = userRepository.findById(followResponse.getId())
                            .orElse(null);

                    if (user == null || user.getEmail() == null) {
                        return null; // Bỏ qua nếu không tìm thấy user hoặc không có email
                    }

                    return InvitationSuggestionResponse.builder()
                            .id(user.getId())
                            .firstName(user.getFirstName())
                            .lastName(user.getLastName())
                            .fullName(user.getFullName())
                            .email(user.getEmail())
                            .avatarUrl(user.getAvatarUrl())
                            .build();
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        // 5. Tạo Page mới với dữ liệu đã lọc
        Page<InvitationSuggestionResponse> filteredPage = new PageImpl<>(
                suggestions,
                pageable,
                followingPage.getTotalElements() // Giữ nguyên total để phân trang đúng
        );

        return PageResponse.fromPage(filteredPage);
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