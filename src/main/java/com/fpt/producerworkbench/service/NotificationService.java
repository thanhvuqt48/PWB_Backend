package com.fpt.producerworkbench.service;

import com.fpt.producerworkbench.dto.request.SendNotificationRequest;
import com.fpt.producerworkbench.dto.response.NotificationResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface NotificationService {

    void sendNotification(SendNotificationRequest request);

    Page<NotificationResponse> getUserNotifications(Long userId, Pageable pageable);

    Long getUnreadCount(Long userId);

    void markAsRead(Long notificationId, Long userId);

    void markAllAsRead(Long userId);
}

