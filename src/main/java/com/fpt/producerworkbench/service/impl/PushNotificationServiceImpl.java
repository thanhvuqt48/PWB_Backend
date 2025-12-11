package com.fpt.producerworkbench.service.impl;

import com.fpt.producerworkbench.dto.request.FcmTokenRequest;
import com.fpt.producerworkbench.dto.response.PushNotificationResponse;
import com.fpt.producerworkbench.entity.FcmToken;
import com.fpt.producerworkbench.entity.User;
import com.fpt.producerworkbench.exception.AppException;
import com.fpt.producerworkbench.exception.ErrorCode;
import com.fpt.producerworkbench.repository.FcmTokenRepository;
import com.fpt.producerworkbench.repository.UserRepository;
import com.fpt.producerworkbench.service.PushNotificationService;
import com.google.firebase.messaging.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class PushNotificationServiceImpl implements PushNotificationService {

    private static final Logger log = LoggerFactory.getLogger(PushNotificationServiceImpl.class);

    private final FirebaseMessaging firebaseMessaging;
    private final FcmTokenRepository fcmTokenRepository;
    private final UserRepository userRepository;

    public PushNotificationServiceImpl(
            FirebaseMessaging firebaseMessaging,
            FcmTokenRepository fcmTokenRepository,
            UserRepository userRepository) {
        this.firebaseMessaging = firebaseMessaging;
        this.fcmTokenRepository = fcmTokenRepository;
        this.userRepository = userRepository;
    }

    @Override
    @Transactional
    public void registerToken(FcmTokenRequest request) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        Optional<FcmToken> existingToken = fcmTokenRepository.findByToken(request.getToken());
        
        if (existingToken.isPresent()) {
            FcmToken token = existingToken.get();
            token.setUser(user);
            token.setDeviceType(request.getDeviceType());
            token.setBrowser(request.getBrowser());
            token.setIsActive(true);
            fcmTokenRepository.save(token);
            log.info("✅ Updated FCM token for user: {}", user.getEmail());
        } else {
            FcmToken newToken = FcmToken.builder()
                    .user(user)
                    .token(request.getToken())
                    .deviceType(request.getDeviceType())
                    .browser(request.getBrowser())
                    .isActive(true)
                    .build();
            fcmTokenRepository.save(newToken);
            log.info("✅ Registered new FCM token for user: {}", user.getEmail());
        }
    }

    @Override
    @Transactional
    public void unregisterToken(String token) {
        fcmTokenRepository.deactivateToken(token);
        log.info("✅ Deactivated FCM token");
    }

    @Override
    public PushNotificationResponse sendToUser(Long userId, String title, String body, Map<String, String> data) {
        List<FcmToken> tokens = fcmTokenRepository.findActiveTokensByUserId(userId);
        
        if (tokens.isEmpty()) {
            log.debug("No active FCM tokens found for user: {}", userId);
            return PushNotificationResponse.failure("No active tokens for user");
        }

        int successCount = 0;
        int failureCount = 0;

        for (FcmToken token : tokens) {
            boolean sent = sendToToken(token.getToken(), title, body, data);
            if (sent) {
                successCount++;
            } else {
                failureCount++;
            }
        }

        return PushNotificationResponse.partial(successCount, failureCount);
    }

    @Override
    public PushNotificationResponse sendToUsers(List<Long> userIds, String title, String body, Map<String, String> data) {
        List<FcmToken> tokens = fcmTokenRepository.findActiveTokensByUserIds(userIds);
        
        if (tokens.isEmpty()) {
            log.debug("No active FCM tokens found for users: {}", userIds);
            return PushNotificationResponse.failure("No active tokens for users");
        }

        List<String> tokenStrings = tokens.stream()
                .map(FcmToken::getToken)
                .collect(Collectors.toList());

        return sendMulticast(tokenStrings, title, body, data);
    }

    @Override
    public boolean sendToToken(String token, String title, String body, Map<String, String> data) {
        try {
            // Only send data payload - let Service Worker handle notification display
            // This prevents duplicate notifications
            Map<String, String> payload = new HashMap<>();
            payload.put("title", title);
            payload.put("body", body);
            if (data != null) {
                payload.putAll(data);
            }

            Message message = Message.builder()
                    .setToken(token)
                    .putAllData(payload)
                    // Use WebpushConfig for web-specific settings
                    .setWebpushConfig(WebpushConfig.builder()
                            .putHeader("Urgency", "high")
                            .putHeader("TTL", "86400") // 24 hours
                            .build())
                    .build();

            String response = firebaseMessaging.send(message);
            log.debug("✅ Push notification sent successfully: {}", response);
            
            // Update last used timestamp
            fcmTokenRepository.updateLastUsed(token);
            
            return true;
        } catch (FirebaseMessagingException e) {
            log.error("❌ Failed to send push notification: {}", e.getMessage());
            
            // If token is invalid/expired, deactivate it
            if (isInvalidTokenError(e)) {
                fcmTokenRepository.deactivateToken(token);
                log.info("Deactivated invalid FCM token");
            }
            
            return false;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public void sendChatNotification(Long recipientUserId, String senderName, String senderAvatar,
                                     String messageContent, String conversationId) {
        List<FcmToken> tokens = fcmTokenRepository.findActiveTokensByUserId(recipientUserId);
        
        if (tokens.isEmpty()) {
            log.debug("No FCM tokens for user {}, skipping push notification", recipientUserId);
            return;
        }

        // Format: "Tin nhắn từ [SenderName]"
        String title = "Tin nhắn từ " + senderName;
        String body = truncateMessage(messageContent, 100);

        Map<String, String> data = new HashMap<>();
        data.put("type", "CHAT_MESSAGE");
        data.put("conversationId", conversationId);
        data.put("senderName", senderName);
        // Always include senderAvatar (empty string if null) so frontend can handle
        data.put("senderAvatar", senderAvatar != null ? senderAvatar : "");
        data.put("clickAction", "/chat?conversationId=" + conversationId);
        
        log.info("📱 Sending chat push notification: sender={}, avatar={}, to user={}", 
                senderName, senderAvatar != null ? "present" : "null", recipientUserId);

        for (FcmToken token : tokens) {
            sendToToken(token.getToken(), title, body, data);
        }
    }

    @Override
    @Transactional
    public void deactivateUserTokens(Long userId) {
        fcmTokenRepository.deactivateAllTokensForUser(userId);
        log.info("✅ Deactivated all FCM tokens for user: {}", userId);
    }

    private PushNotificationResponse sendMulticast(List<String> tokens, String title, String body, Map<String, String> data) {
        if (tokens.isEmpty()) {
            return PushNotificationResponse.failure("No tokens provided");
        }

        try {
            // Only send data payload - let Service Worker handle notification display
            Map<String, String> payload = new HashMap<>();
            payload.put("title", title);
            payload.put("body", body);
            if (data != null) {
                payload.putAll(data);
            }

            MulticastMessage message = MulticastMessage.builder()
                    .addAllTokens(tokens)
                    .putAllData(payload)
                    .setWebpushConfig(WebpushConfig.builder()
                            .putHeader("Urgency", "high")
                            .putHeader("TTL", "86400")
                            .build())
                    .build();

            BatchResponse response = firebaseMessaging.sendEachForMulticast(message);
            
            log.info("✅ Multicast sent: {} success, {} failure",
                    response.getSuccessCount(), response.getFailureCount());

            // Deactivate invalid tokens
            handleMulticastFailures(tokens, response);

            return PushNotificationResponse.partial(response.getSuccessCount(), response.getFailureCount());
        } catch (FirebaseMessagingException e) {
            log.error("❌ Failed to send multicast notification: {}", e.getMessage());
            return PushNotificationResponse.failure(e.getMessage());
        }
    }

    private void handleMulticastFailures(List<String> tokens, BatchResponse response) {
        List<SendResponse> responses = response.getResponses();
        for (int i = 0; i < responses.size(); i++) {
            SendResponse sendResponse = responses.get(i);
            if (!sendResponse.isSuccessful()) {
                FirebaseMessagingException exception = sendResponse.getException();
                if (exception != null && isInvalidTokenError(exception)) {
                    String invalidToken = tokens.get(i);
                    fcmTokenRepository.deactivateToken(invalidToken);
                    log.debug("Deactivated invalid token at index {}", i);
                }
            }
        }
    }

    private boolean isInvalidTokenError(FirebaseMessagingException e) {
        MessagingErrorCode errorCode = e.getMessagingErrorCode();
        return errorCode == MessagingErrorCode.UNREGISTERED ||
               errorCode == MessagingErrorCode.INVALID_ARGUMENT;
    }

    private String truncateMessage(String message, int maxLength) {
        if (message == null) return "";
        if (message.length() <= maxLength) return message;
        return message.substring(0, maxLength - 3) + "...";
    }
}
