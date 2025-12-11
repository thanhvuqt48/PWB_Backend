package com.fpt.producerworkbench.service;

import com.fpt.producerworkbench.dto.request.FcmTokenRequest;
import com.fpt.producerworkbench.dto.response.PushNotificationResponse;

import java.util.List;
import java.util.Map;

/**
 * Service interface for Push Notification operations
 */
public interface PushNotificationService {

    /**
     * Register FCM token for current user
     */
    void registerToken(FcmTokenRequest request);

    /**
     * Unregister/Remove FCM token
     */
    void unregisterToken(String token);

    /**
     * Send push notification to a specific user
     */
    PushNotificationResponse sendToUser(Long userId, String title, String body, Map<String, String> data);

    /**
     * Send push notification to multiple users
     */
    PushNotificationResponse sendToUsers(List<Long> userIds, String title, String body, Map<String, String> data);

    /**
     * Send push notification to a specific token
     */
    boolean sendToToken(String token, String title, String body, Map<String, String> data);

    /**
     * Send chat message notification
     */
    void sendChatNotification(Long recipientUserId, String senderName, String senderAvatar, 
                               String messageContent, String conversationId);

    /**
     * Deactivate all tokens for a user (on logout)
     */
    void deactivateUserTokens(Long userId);
}
