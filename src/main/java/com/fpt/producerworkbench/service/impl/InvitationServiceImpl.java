package com.fpt.producerworkbench.service.impl;

import com.fpt.producerworkbench.common.InvitationStatus;
import com.fpt.producerworkbench.common.ProjectRole;
import com.fpt.producerworkbench.dto.event.NotificationEvent;
import com.fpt.producerworkbench.dto.projection.ProjectBasicInfo;
import com.fpt.producerworkbench.dto.response.PageResponse;
import com.fpt.producerworkbench.dto.request.InvitationRequest;
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
    private final AsyncNotificationService asyncNotificationService;
    private final FollowService followService;

    private static final String NOTIFICATION_TOPIC = "notification-delivery";

    @Override
    @Transactional
    public String createInvitation(Long projectId, InvitationRequest request, User inviter) {
        log.info("Bắt đầu tạo lời mời cho dự án {} bởi {}", projectId, inviter.getEmail());

        // Dùng Projection để lấy thông tin cơ bản, tránh load liveSessions
        ProjectBasicInfo projectInfo = projectRepository.findBasicInfoById(projectId)
                .orElseThrow(() -> new AppException(ErrorCode.PROJECT_NOT_FOUND));

        if (!projectInfo.getCreatorId().equals(inviter.getId())) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }

        if (request.getRole() == ProjectRole.CLIENT && projectInfo.getClientId() != null) {
            throw new AppException(ErrorCode.CLIENT_ALREADY_EXISTS);
        }

        if (inviter.getEmail().equalsIgnoreCase(request.getEmail())) {
            throw new AppException(ErrorCode.INVITATION_SELF_NOT_ALLOWED);
        }

        boolean alreadyMember = projectMemberRepository.findByProjectIdAndUserEmail(projectId, request.getEmail())
                .isPresent()
                || projectInfo.getCreatorEmail().equalsIgnoreCase(request.getEmail());
        
        // Kiểm tra client nếu có
        if (projectInfo.getClientId() != null) {
            userRepository.findById(projectInfo.getClientId()).ifPresent(client -> {
                if (client.getEmail().equalsIgnoreCase(request.getEmail())) {
                    throw new AppException(ErrorCode.USER_ALREADY_MEMBER);
                }
            });
        }
        
        if (alreadyMember) {
            throw new AppException(ErrorCode.USER_ALREADY_MEMBER);
        }

        // Vô hiệu hóa tất cả lời mời PENDING cũ
        try {
            invitationRepository.expirePendingInvitationsForEmail(projectId, request.getEmail());
        } catch (Exception ex) {
            log.warn("Không thể expire lời mời cũ cho {} trong project {}", request.getEmail(), projectId, ex);
        }

        String token = UUID.randomUUID().toString();
        boolean collaboratorAnonymous = Boolean.TRUE.equals(request.getAnonymous())
                && request.getRole() == ProjectRole.COLLABORATOR;

        // Dùng getReferenceById() - JPA standard
        Project projectRef = projectRepository.getReferenceById(projectId);

        ProjectInvitation invitation = ProjectInvitation.builder()
                .project(projectRef)
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
        String projectTitle = projectInfo.getTitle();
        String inviterFullName = inviter.getFullName();
        String inviterEmail = inviter.getEmail();

        try {
            NotificationEvent event = NotificationEvent.builder()
                    .recipient(savedInvitation.getEmail())
                    .subject("Lời mời tham gia dự án: " + projectTitle)
                    .templateCode("invitation-email-template")
                    .param(Map.of(
                            "inviterName", inviterFullName,
                            "projectName", projectTitle,
                            "role", savedInvitation.getInvitedRole().name(),
                            "invitationLink", invitationLink))
                    .build();
            kafkaTemplate.send(NOTIFICATION_TOPIC, event);
            log.info("Đã gửi message mời tới Kafka thành công!");
        } catch (Exception e) {
            log.error("Gặp lỗi khi gửi message mời tới Kafka!", e);
        }

        // Gửi notification bất đồng bộ nếu user đã tồn tại trong hệ thống
        userRepository.findByEmail(savedInvitation.getEmail()).ifPresent(user -> {
            String roleName = savedInvitation.getInvitedRole() == ProjectRole.COLLABORATOR ? "Cộng tác viên"
                    : savedInvitation.getInvitedRole() == ProjectRole.OBSERVER ? "Người quan sát"
                            : "Khách hàng";
            asyncNotificationService.sendNewInvitationNotification(
                    user.getId(),
                    projectId,
                    projectTitle,
                    inviterFullName != null ? inviterFullName : inviterEmail,
                    roleName);
        });

        return invitationLink;
    }

    @Override
    @Transactional
    public void acceptInvitationById(Long invitationId, User acceptingUser) {
        ProjectInvitation invitation = invitationRepository.findById(invitationId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND));

        // Reuse existing validation logic
        validateInvitation(invitation, acceptingUser);

        // Lấy projectId từ invitation trước
        Long projectId = invitation.getProject().getId();

        // Dùng Projection để lấy thông tin, tránh load liveSessions
        ProjectBasicInfo projectInfo = projectRepository.findBasicInfoById(projectId)
                .orElseThrow(() -> new AppException(ErrorCode.PROJECT_NOT_FOUND));

        // Lưu lại thông tin cần thiết
        String projectTitle = projectInfo.getTitle();
        Long ownerId = projectInfo.getCreatorId();
        String ownerEmail = projectInfo.getCreatorEmail();
        String ownerFullName = projectInfo.getCreatorFullName();
        ProjectRole invitedRole = invitation.getInvitedRole();
        boolean collaboratorAnonymous = Boolean.TRUE.equals(invitation.getCollaboratorAnonymous());

        // Kiểm tra CLIENT đã tồn tại chưa bằng query thay vì load entity
        if (invitedRole == ProjectRole.CLIENT) {
            if (projectRepository.hasClient(projectId)) {
                throw new AppException(ErrorCode.CLIENT_EXISTED);
            }
        }

        boolean anonymousFlag = collaboratorAnonymous && invitedRole == ProjectRole.COLLABORATOR;

        // Sử dụng getReferenceById() - JPA standard method
        Project projectRef = projectRepository.getReferenceById(projectId);
        User userRef = userRepository.getReferenceById(acceptingUser.getId());

        ProjectMember newMember = ProjectMember.builder()
                .project(projectRef)
                .user(userRef)
                .projectRole(invitedRole)
                .anonymous(anonymousFlag)
                .build();
        projectMemberRepository.save(newMember);

        if (invitedRole == ProjectRole.CLIENT) {
            // Sử dụng native query để update client, tránh cascade đến liveSessions
            projectRepository.updateClientById(projectId, acceptingUser.getId());
        }

        invitation.setStatus(InvitationStatus.ACCEPTED);
        invitationRepository.save(invitation);

        // Gửi email thông báo - wrap trong try-catch để không rollback transaction nếu Kafka lỗi
        try {
            NotificationEvent confirmationEvent = NotificationEvent.builder()
                    .recipient(acceptingUser.getEmail())
                    .subject("Chào mừng bạn đến với dự án: " + projectTitle)
                    .templateCode("invitation-accepted-confirmation-template")
                    .param(Map.of(
                            "userName", acceptingUser.getFullName(),
                            "projectName", projectTitle,
                            "role", invitedRole.name()))
                    .build();
            kafkaTemplate.send(NOTIFICATION_TOPIC, confirmationEvent);

            NotificationEvent ownerNotificationEvent = NotificationEvent.builder()
                    .recipient(ownerEmail)
                    .subject("Thành viên mới đã tham gia dự án của bạn")
                    .templateCode("owner-notification-new-member-template")
                    .param(Map.of(
                            "ownerName", ownerFullName,
                            "newMemberName", acceptingUser.getFullName(),
                            "newMemberEmail", acceptingUser.getEmail(),
                            "projectName", projectTitle,
                            "role", invitedRole.name()))
                    .build();
            kafkaTemplate.send(NOTIFICATION_TOPIC, ownerNotificationEvent);
            log.info("Đã gửi email thông báo accept invitation thành công");
        } catch (Exception e) {
            log.error("Lỗi khi gửi email thông báo accept invitation, nhưng invitation vẫn được accept", e);
        }

        // Gửi notification bất đồng bộ - chỉ truyền primitive data
        String roleName = invitedRole == ProjectRole.COLLABORATOR ? "Cộng tác viên"
                : invitedRole == ProjectRole.OBSERVER ? "Người quan sát"
                        : "Khách hàng";

        asyncNotificationService.sendInvitationAcceptedNotification(
                ownerId,
                projectId,
                projectTitle,
                acceptingUser.getFullName(),
                acceptingUser.getEmail(),
                roleName);
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

        // Lấy projectId trước
        Long projectId = invitation.getProject().getId();
        
        // Dùng Projection để lấy thông tin, tránh load liveSessions
        ProjectBasicInfo projectInfo = projectRepository.findBasicInfoById(projectId)
                .orElseThrow(() -> new AppException(ErrorCode.PROJECT_NOT_FOUND));

        if (!projectInfo.getCreatorId().equals(currentUser.getId())) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }

        if (invitation.getStatus() != InvitationStatus.PENDING) {
            throw new AppException(ErrorCode.INVITATION_NOT_CANCELABLE);
        }

        // Lưu primitive data
        String projectTitle = projectInfo.getTitle();
        String inviteeEmail = invitation.getEmail();

        invitation.setStatus(InvitationStatus.CANCELLED);
        invitationRepository.save(invitation);

        // Gửi email thông báo - wrap trong try-catch để không rollback transaction nếu Kafka lỗi
        try {
            NotificationEvent event = NotificationEvent.builder()
                    .recipient(inviteeEmail)
                    .subject("Lời mời tham gia dự án \"" + projectTitle + "\" đã được hủy")
                    .templateCode("invitation-cancelled-template")
                    .param(Map.of("projectName", projectTitle))
                    .build();
            kafkaTemplate.send(NOTIFICATION_TOPIC, event);
            log.info("Đã gửi email thông báo cancel invitation thành công");
        } catch (Exception e) {
            log.error("Lỗi khi gửi email thông báo cancel invitation, nhưng invitation vẫn được cancel", e);
        }

        // Gửi notification bất đồng bộ nếu user đã tồn tại trong hệ thống
        userRepository.findByEmail(inviteeEmail).ifPresent(user -> {
            asyncNotificationService.sendInvitationCancelledNotification(
                    user.getId(),
                    projectId,
                    projectTitle);
        });
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

        // Lấy projectId từ invitation
        Long projectId = invitation.getProject().getId();
        
        // Dùng Projection để lấy thông tin, tránh load liveSessions
        ProjectBasicInfo projectInfo = projectRepository.findBasicInfoById(projectId)
                .orElseThrow(() -> new AppException(ErrorCode.PROJECT_NOT_FOUND));
        
        String projectTitle = projectInfo.getTitle();
        Long ownerId = projectInfo.getCreatorId();
        String ownerEmail = projectInfo.getCreatorEmail();

        invitation.setStatus(InvitationStatus.DECLINED);
        invitationRepository.save(invitation);

        // Gửi email thông báo - wrap trong try-catch để không rollback transaction nếu Kafka lỗi
        try {
            NotificationEvent event = NotificationEvent.builder()
                    .recipient(ownerEmail)
                    .subject("Thông báo: Lời mời tham gia dự án đã bị từ chối")
                    .templateCode("invitation-declined-template")
                    .param(Map.of(
                            "projectName", projectTitle,
                            "inviteeEmail", currentUser.getEmail(),
                            "inviteeName", currentUser.getFullName()))
                    .build();
            kafkaTemplate.send(NOTIFICATION_TOPIC, event);
            log.info("Đã gửi email thông báo decline invitation thành công");
        } catch (Exception e) {
            log.error("Lỗi khi gửi email thông báo decline invitation, nhưng invitation vẫn được decline", e);
        }

        // Gửi notification bất đồng bộ
        asyncNotificationService.sendInvitationDeclinedNotification(
                ownerId,
                projectId,
                projectTitle,
                currentUser.getFullName(),
                currentUser.getEmail());
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