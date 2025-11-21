package com.fpt.producerworkbench.service.impl;

import com.fpt.producerworkbench.dto.event.RealtimeNotificationEvent;
import com.fpt.producerworkbench.dto.request.SendNotificationRequest;
import com.fpt.producerworkbench.dto.response.NotificationResponse;
import com.fpt.producerworkbench.entity.Notification;
import com.fpt.producerworkbench.entity.User;
import com.fpt.producerworkbench.exception.AppException;
import com.fpt.producerworkbench.exception.ErrorCode;
import com.fpt.producerworkbench.kafka.producer.RealtimeNotificationProducer;
import com.fpt.producerworkbench.repository.NotificationRepository;
import com.fpt.producerworkbench.repository.UserRepository;
import com.fpt.producerworkbench.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final RealtimeNotificationProducer notificationProducer;

    @Override
    @Transactional
    public void sendNotification(SendNotificationRequest request) {
        log.info("Sending notification to user {}: type={}, title={}", request.getUserId(), request.getType(), request.getTitle());

        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        Notification notification = Notification.builder()
                .type(request.getType())
                .title(request.getTitle())
                .message(request.getMessage())
                .user(user)
                .isRead(false)
                .relatedEntityType(request.getRelatedEntityType())
                .relatedEntityId(request.getRelatedEntityId())
                .actionUrl(request.getActionUrl())
                .build();

        Notification saved = notificationRepository.save(notification);
        log.info("Notification saved to DB: id={}", saved.getId());

        RealtimeNotificationEvent event = RealtimeNotificationEvent.builder()
                .notificationId(saved.getId())
                .userId(request.getUserId())
                .type(request.getType())
                .title(request.getTitle())
                .message(request.getMessage())
                .relatedEntityType(request.getRelatedEntityType())
                .relatedEntityId(request.getRelatedEntityId())
                .actionUrl(request.getActionUrl())
                .build();

        notificationProducer.sendNotification(event);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<NotificationResponse> getUserNotifications(Long userId, Pageable pageable) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
                .map(this::mapToResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Long getUnreadCount(Long userId) {
        return notificationRepository.countUnreadByUserId(userId);
    }

    @Override
    @Transactional
    public void markAsRead(Long notificationId, Long userId) {
        int updated = notificationRepository.markAsReadByIdAndUserId(notificationId, userId);
        if (updated == 0) {
            throw new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Notification not found");
        }
        log.info("Notification {} marked as read by user {}", notificationId, userId);
    }

    @Override
    @Transactional
    public void markAllAsRead(Long userId) {
        int updated = notificationRepository.markAllAsReadByUserId(userId);
        log.info("Marked {} notifications as read for user {}", updated, userId);
    }

    private NotificationResponse mapToResponse(Notification notification) {
        return NotificationResponse.builder()
                .id(notification.getId())
                .type(notification.getType())
                .title(notification.getTitle())
                .message(notification.getMessage())
                .isRead(notification.getIsRead())
                .relatedEntityType(notification.getRelatedEntityType())
                .relatedEntityId(notification.getRelatedEntityId())
                .actionUrl(notification.getActionUrl())
                .createdAt(notification.getCreatedAt())
                .build();
    }
}

