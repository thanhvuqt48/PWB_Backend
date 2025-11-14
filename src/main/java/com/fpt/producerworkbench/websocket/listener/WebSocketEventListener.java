package com.fpt.producerworkbench.websocket.listener;

import com.fpt.producerworkbench.entity.Conversation;
import com.fpt.producerworkbench.entity.User;
import com.fpt.producerworkbench.entity.WebSocketSession;
import com.fpt.producerworkbench.repository.ConversationRepository;
import com.fpt.producerworkbench.repository.UserRepository;
import com.fpt.producerworkbench.service.SessionParticipantService;
import com.fpt.producerworkbench.service.WebSocketService;
import com.fpt.producerworkbench.service.impl.WebSocketSessionRedisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class WebSocketEventListener {

    private final WebSocketSessionRedisService sessionRedisService;
    private final SessionParticipantService participantService;
    private final WebSocketService webSocketService;
    private final ConversationRepository conversationRepository;
    private final UserRepository userRepository;

    @Async
    @EventListener
    public void handleSessionConnect(SessionConnectEvent connectEvent) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(connectEvent.getMessage());
        Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
        String sessionId = accessor.getSessionId();
        Principal user = connectEvent.getUser();

        if(user == null) {
            throw new RuntimeException("Unauthenticated");
        }

        String userEmail = user.getName();
        Long userId = sessionAttributes != null && sessionAttributes.get("userId") != null
                ? ((Number) sessionAttributes.get("userId")).longValue()
                : null;
        String liveSessionId = sessionAttributes != null && sessionAttributes.get("liveSessionId") != null
                ? sessionAttributes.get("liveSessionId").toString()
                : null;

        log.info("🔌 WebSocket CONNECT event - WsSession: {}, User: {}, UserId: {}, LiveSession: {}",
                sessionId,
                userEmail,
                userId,
                liveSessionId);

        sessionRedisService.saveWebSocketSession(WebSocketSession.builder()
                .socketSessionId(sessionId)
                .userId(userEmail)
                .build());
        log.info("✅ Saved WebSocket session to Redis: wsSession={}, userId={}", sessionId, userId);

        // Broadcast online status to all conversations this user is part of
        try {
            User currentUser = userRepository.findByEmail(userEmail).orElse(null);
            if (currentUser != null) {
                List<Conversation> conversations = conversationRepository.findByParticipantsUserId(currentUser.getId());
                for (Conversation conversation : conversations) {
                    webSocketService.broadcastUserStatusChange(userEmail, true, conversation.getId());
                }
                log.info("✅ Broadcasted online status for user {} to {} conversations", userEmail, conversations.size());
            }
        } catch (Exception e) {
            log.error("❌ Failed to broadcast online status for user {}: {}", userEmail, e.getMessage(), e);
        }
    }

    @Async
    @EventListener
    public void handleSessionDisConnect(SessionDisconnectEvent disconnectEvent) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(disconnectEvent.getMessage());
        String wsSessionId = accessor.getSessionId();
        Principal user = disconnectEvent.getUser();
        String userEmail = user != null ? user.getName() : null;

        sessionRedisService.deleteWebsocketSession(wsSessionId);
        Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
        if (sessionAttributes == null) {
            log.warn("⚠️ Session attributes null for wsSession: {}", wsSessionId);
            return;
        }

        Object userIdObj = sessionAttributes.get("userId");
        Long userId = null;
        if (userIdObj instanceof Number) {
            userId = ((Number) userIdObj).longValue();
        } else if (userIdObj instanceof String) {
            try {
                userId = Long.parseLong((String) userIdObj);
            } catch (NumberFormatException ignored) {
            }
        }

        String liveSessionId = sessionAttributes.get("liveSessionId") != null
                ? sessionAttributes.get("liveSessionId").toString()
                : null;

        log.info("🔌 WebSocket DISCONNECTED - WsSession: {}, User: {}, User ID: {}, LiveSession: {}",
                wsSessionId, userEmail, userId, liveSessionId);

        if (userId != null && liveSessionId != null) {
            try {
                log.info("🧹 Cleaning up participant {} from session {}", userId, liveSessionId);
                participantService.handleParticipantDisconnect(liveSessionId, userId);
                log.info("✅ Participant {} successfully cleaned up from session {}", userId, liveSessionId);
            } catch (Exception e) {
                log.error("❌ Failed to cleanup participant {} from session {}: {}",
                        userId, liveSessionId, e.getMessage(), e);
            }
        } else {
            log.debug("ℹ️ WebSocket disconnected but no active live session (userId: {}, sessionId: {})",
                    userId, liveSessionId);
        }

        if (userEmail != null) {
            try {
                User currentUser = userRepository.findByEmail(userEmail).orElse(null);
                if (currentUser != null) {
                    List<Conversation> conversations = conversationRepository.findByParticipantsUserId(currentUser.getId());
                    for (Conversation conversation : conversations) {
                        webSocketService.broadcastUserStatusChange(userEmail, false, conversation.getId());
                    }
                    log.info("✅ Broadcasted offline status for user {} to {} conversations", userEmail, conversations.size());
                }
            } catch (Exception e) {
                log.error("❌ Failed to broadcast offline status for user {}: {}", userEmail, e.getMessage(), e);
            }
        }

        log.info("Disconnected from websocket session {}", accessor.getSessionId());
    }

}
