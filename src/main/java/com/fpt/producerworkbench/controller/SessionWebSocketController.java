package com.fpt.producerworkbench.controller;

import com.fpt.producerworkbench.dto.websocket.ChatMessage;
import com.fpt.producerworkbench.dto.websocket.PlaybackEvent;
import com.fpt.producerworkbench.entity.User;
import com.fpt.producerworkbench.exception.AppException;
import com.fpt.producerworkbench.exception.ErrorCode;
import com.fpt.producerworkbench.repository.UserRepository;
import com.fpt.producerworkbench.service.WebSocketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
@Slf4j
public class SessionWebSocketController {

    private final WebSocketService webSocketService;
    private final UserRepository userRepository;

    /**
     * Handle chat messages
     * Client sends to: /app/session/{sessionId}/chat
     * Server broadcasts to: /topic/session/{sessionId}/chat
     */
    @MessageMapping("/session/{sessionId}/chat")
    public void sendChatMessage(
            @DestinationVariable String sessionId,
            @Payload ChatMessage message,
            SimpMessageHeaderAccessor headerAccessor) {  // ✅ Thay Principal bằng SimpMessageHeaderAccessor

        log.info("📨 Received chat message in session {}", sessionId);

        try {
            // ✅ Get userId from session attributes (stored during CONNECT)
            Long userId = (Long) headerAccessor.getSessionAttributes().get("userId");

            if (userId == null) {
                log.warn("⚠️ Anonymous chat message in session {} - No userId in session", sessionId);
                sendAnonymousMessage(sessionId, message);
                return;
            }

            // ✅ Get user from database
            User sender = userRepository.findById(userId)
                    .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

            // Build complete message
            ChatMessage completeMessage = ChatMessage.builder()
                    .messageId(UUID.randomUUID().toString())
                    .sessionId(sessionId)
                    .senderId(sender.getId())
                    .senderName(sender.getFirstName() + " " + sender.getLastName())
                    .senderAvatarUrl(sender.getAvatarUrl())
                    .content(message.getContent())
                    .type(message.getType() != null ? message.getType() : "TEXT")
                    .timestamp(LocalDateTime.now())
                    .build();

            webSocketService.broadcastChatMessage(sessionId, completeMessage);

            log.info("✅ Chat message broadcasted from {} (ID: {})", sender.getEmail(), userId);

        } catch (Exception e) {
            log.error("❌ Error handling chat message: {}", e.getMessage(), e);
        }
    }

    /**
     * Handle playback control events
     * Client sends to: /app/session/{sessionId}/playback
     * Server broadcasts to: /topic/session/{sessionId}/playback
     */
    @MessageMapping("/session/{sessionId}/playback")
    public void controlPlayback(
            @DestinationVariable String sessionId,
            @Payload PlaybackEvent event,
            SimpMessageHeaderAccessor headerAccessor) {  // ✅ Changed

        log.info("🎵 Received playback event in session {}: {}", sessionId, event.getAction());

        try {
            Long userId = (Long) headerAccessor.getSessionAttributes().get("userId");

            if (userId == null) {
                log.warn("⚠️ Anonymous playback control in session {}", sessionId);
                event.setTriggeredByUserId(0L);
                event.setTriggeredByUserName("Anonymous");
            } else {
                User user = userRepository.findById(userId)
                        .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

                event.setTriggeredByUserId(user.getId());
                event.setTriggeredByUserName(user.getFirstName() + " " + user.getLastName());
            }

            webSocketService.broadcastPlaybackEvent(sessionId, event);
            log.info("✅ Playback event broadcasted: {}", event.getAction());

        } catch (Exception e) {
            log.error("❌ Error handling playback event: {}", e.getMessage(), e);
        }
    }

    /**
     * Handle user connection to session
     * Client sends to: /app/session/{sessionId}/connect
     */
    @MessageMapping("/session/{sessionId}/connect")
    @SendToUser("/queue/reply")
    public String handleConnect(
            @DestinationVariable String sessionId,
            SimpMessageHeaderAccessor headerAccessor,
            Principal principal) {

        // ✅ Check if principal exists
        String email = (principal != null) ? principal.getName() : "anonymous";

        // Store session ID in WebSocket session attributes
        String sessionIdAttr = (String) headerAccessor.getSessionAttributes().get("sessionId");
        if (sessionIdAttr == null) {
            headerAccessor.getSessionAttributes().put("sessionId", sessionId);
            headerAccessor.getSessionAttributes().put("userEmail", email);
        }

        log.info("🔌 User {} connected to session: {}", email, sessionId);

        return "Connected to session: " + sessionId;
    }

    /**
     * Handle user disconnection
     * Client sends to: /app/session/{sessionId}/disconnect
     */
    @MessageMapping("/session/{sessionId}/disconnect")
    public void handleDisconnect(
            @DestinationVariable String sessionId,
            Principal principal) {

        String email = (principal != null) ? principal.getName() : "anonymous";
        log.info("🔌 User {} disconnected from session: {}", email, sessionId);

        // Note: Actual disconnect is handled by @EventListener in WebSocketEventListener
    }

    /**
     * Handle typing indicator
     * Client sends to: /app/session/{sessionId}/typing
     * Server broadcasts to: /topic/session/{sessionId}/typing
     */
    @MessageMapping("/session/{sessionId}/typing")
    public void handleTyping(
            @DestinationVariable String sessionId,
            @Payload String action,
            SimpMessageHeaderAccessor headerAccessor) {  // ✅ Changed

        try {
            Long userId = (Long) headerAccessor.getSessionAttributes().get("userId");

            if (userId == null) {
                log.debug("⚠️ Anonymous typing indicator in session {}", sessionId);
                return;
            }

            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

            webSocketService.broadcastParticipantEvent(sessionId,
                    com.fpt.producerworkbench.dto.websocket.ParticipantEvent.builder()
                            .action("TYPING_" + action.toUpperCase())
                            .userId(user.getId())
                            .userName(user.getFirstName() + " " + user.getLastName())
                            .build()
            );

        } catch (Exception e) {
            log.error("❌ Error handling typing indicator: {}", e.getMessage(), e);
        }

    }
    private void sendAnonymousMessage(String sessionId, ChatMessage message) {
        ChatMessage anonymousMessage = ChatMessage.builder()
                .messageId(UUID.randomUUID().toString())
                .sessionId(sessionId)
                .senderId(0L)
                .senderName("Anonymous")
                .senderAvatarUrl(null)
                .content(message.getContent())
                .type(message.getType() != null ? message.getType() : "TEXT")
                .timestamp(LocalDateTime.now())
                .build();

        webSocketService.broadcastChatMessage(sessionId, anonymousMessage);
    }
}
