package com.fpt.producerworkbench.service.impl;

import com.fpt.producerworkbench.common.NotificationType;
import com.fpt.producerworkbench.common.RelatedEntityType;
import com.fpt.producerworkbench.dto.request.SendNotificationRequest;
import com.fpt.producerworkbench.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Service xử lý gửi notification bất đồng bộ.
 * Tách riêng để:
 * 1. @Async hoạt động đúng (Spring AOP proxy không hoạt động với self-invocation)
 * 2. Tránh transaction conflict - chạy trên thread riêng, không share Hibernate session
 * 3. API response nhanh - không chờ notification gửi xong
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AsyncNotificationService {

    private final NotificationService notificationService;

    /**
     * Gửi notification bất đồng bộ cho việc accept invitation.
     * Chỉ nhận primitive data (IDs, Strings) để tránh shared entity references.
     *
     * @param recipientUserId ID của người nhận notification
     * @param projectId       ID của project
     * @param projectTitle    Tên project
     * @param acceptorName    Tên người accept invitation
     * @param acceptorEmail   Email người accept (fallback nếu name null)
     * @param roleName        Tên vai trò (Cộng tác viên, Người quan sát, Khách hàng)
     */
    @Async("taskExecutor")
    public void sendInvitationAcceptedNotification(
            Long recipientUserId,
            Long projectId,
            String projectTitle,
            String acceptorName,
            String acceptorEmail,
            String roleName) {

        log.debug("🚀 [ASYNC] Bắt đầu gửi notification cho user {} về việc accept invitation", recipientUserId);

        try {
            String displayName = acceptorName != null ? acceptorName : acceptorEmail;
            String actionUrl = String.format("/teamInvitation?id=%d", projectId);

            notificationService.sendNotification(
                    SendNotificationRequest.builder()
                            .userId(recipientUserId)
                            .type(NotificationType.PROJECT_INVITATION)
                            .title("Thành viên mới đã tham gia dự án")
                            .message(String.format("%s đã chấp nhận lời mời và tham gia dự án \"%s\" với vai trò %s.",
                                    displayName,
                                    projectTitle,
                                    roleName))
                            .relatedEntityType(RelatedEntityType.PROJECT)
                            .relatedEntityId(projectId)
                            .actionUrl(actionUrl)
                            .build());

            log.info("✅ [ASYNC] Notification đã gửi thành công cho user {}", recipientUserId);

        } catch (Exception e) {
            // Log lỗi nhưng không throw - notification fail không nên ảnh hưởng business logic
            log.error("❌ [ASYNC] Lỗi khi gửi notification cho user {}: {}", recipientUserId, e.getMessage(), e);
        }
    }

    /**
     * Gửi notification bất đồng bộ cho việc tạo invitation mới.
     */
    @Async("taskExecutor")
    public void sendNewInvitationNotification(
            Long recipientUserId,
            Long projectId,
            String projectTitle,
            String inviterName,
            String roleName) {

        log.debug("🚀 [ASYNC] Bắt đầu gửi notification invitation mới cho user {}", recipientUserId);

        try {
            notificationService.sendNotification(
                    SendNotificationRequest.builder()
                            .userId(recipientUserId)
                            .type(NotificationType.PROJECT_INVITATION)
                            .title("Lời mời tham gia dự án")
                            .message(String.format("%s đã mời bạn tham gia dự án \"%s\" với vai trò %s",
                                    inviterName,
                                    projectTitle,
                                    roleName))
                            .relatedEntityType(RelatedEntityType.PROJECT)
                            .relatedEntityId(projectId)
                            .actionUrl("/myInvitations")
                            .build());

            log.info("✅ [ASYNC] Notification invitation mới đã gửi cho user {}", recipientUserId);

        } catch (Exception e) {
            log.error("❌ [ASYNC] Lỗi khi gửi notification invitation mới cho user {}: {}", recipientUserId, e.getMessage(), e);
        }
    }

    /**
     * Gửi notification bất đồng bộ cho việc decline invitation.
     */
    @Async("taskExecutor")
    public void sendInvitationDeclinedNotification(
            Long ownerUserId,
            Long projectId,
            String projectTitle,
            String declinerName,
            String declinerEmail) {

        log.debug("🚀 [ASYNC] Bắt đầu gửi notification decline cho owner {}", ownerUserId);

        try {
            String displayName = declinerName != null ? declinerName : declinerEmail;
            String actionUrl = String.format("/projectDetail?id=%d", projectId);

            notificationService.sendNotification(
                    SendNotificationRequest.builder()
                            .userId(ownerUserId)
                            .type(NotificationType.PROJECT_INVITATION)
                            .title("Lời mời đã bị từ chối")
                            .message(String.format("%s đã từ chối lời mời tham gia dự án \"%s\".",
                                    displayName,
                                    projectTitle))
                            .relatedEntityType(RelatedEntityType.PROJECT)
                            .relatedEntityId(projectId)
                            .actionUrl(actionUrl)
                            .build());

            log.info("✅ [ASYNC] Notification decline đã gửi cho owner {}", ownerUserId);

        } catch (Exception e) {
            log.error("❌ [ASYNC] Lỗi khi gửi notification decline cho owner {}: {}", ownerUserId, e.getMessage(), e);
        }
    }

    /**
     * Gửi notification bất đồng bộ cho việc cancel invitation.
     */
    @Async("taskExecutor")
    public void sendInvitationCancelledNotification(
            Long recipientUserId,
            Long projectId,
            String projectTitle) {

        log.debug("🚀 [ASYNC] Bắt đầu gửi notification cancel cho user {}", recipientUserId);

        try {
            notificationService.sendNotification(
                    SendNotificationRequest.builder()
                            .userId(recipientUserId)
                            .type(NotificationType.PROJECT_INVITATION)
                            .title("Lời mời đã được hủy")
                            .message(String.format("Lời mời tham gia dự án \"%s\" đã được hủy bởi chủ dự án.",
                                    projectTitle))
                            .relatedEntityType(RelatedEntityType.PROJECT)
                            .relatedEntityId(projectId)
                            .actionUrl("/myInvitations")
                            .build());

            log.info("✅ [ASYNC] Notification cancel đã gửi cho user {}", recipientUserId);

        } catch (Exception e) {
            log.error("❌ [ASYNC] Lỗi khi gửi notification cancel cho user {}: {}", recipientUserId, e.getMessage(), e);
        }
    }
}
