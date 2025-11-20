package com.fpt.producerworkbench.websocket.listener;

import com.fpt.producerworkbench.dto.websocket.JoinRequest;
import com.fpt.producerworkbench.dto.websocket.SystemNotification;
import com.fpt.producerworkbench.entity.LiveSession;
import com.fpt.producerworkbench.entity.WebSocketSession;
import com.fpt.producerworkbench.repository.LiveSessionRepository;
import com.fpt.producerworkbench.service.SessionParticipantService;
import com.fpt.producerworkbench.service.WebSocketService;
import com.fpt.producerworkbench.service.impl.JoinRequestRedisService;
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
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class WebSocketEventListener {

    private final WebSocketSessionRedisService sessionRedisService;
    private final SessionParticipantService participantService;
    private final JoinRequestRedisService joinRequestRedisService;
    private final WebSocketService webSocketService;
    private final LiveSessionRepository liveSessionRepository;

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

        Long userId = sessionAttributes != null && sessionAttributes.get("userId") != null
                ? ((Number) sessionAttributes.get("userId")).longValue()
                : null;
        String liveSessionId = sessionAttributes != null && sessionAttributes.get("liveSessionId") != null
                ? sessionAttributes.get("liveSessionId").toString()
                : null;

        log.info("🔌 WebSocket CONNECT event - WsSession: {}, User: {}, UserId: {}, LiveSession: {}",
                sessionId,
                user != null ? user.getName() : "null",
                userId,
                liveSessionId);

        sessionRedisService.saveWebSocketSession(WebSocketSession.builder()
                .socketSessionId(sessionId)
                .userId(user.getName())
                .build());
        log.info("✅ Saved WebSocket session to Redis: wsSession={}, userId={}", sessionId, userId);
    }

    @Async
    @EventListener
    public void handleSessionDisConnect(SessionDisconnectEvent disconnectEvent) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(disconnectEvent.getMessage());
        String wsSessionId = accessor.getSessionId();
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

        log.info("🔌 WebSocket DISCONNECTED - WsSession: {}, User ID: {}, LiveSession: {}",
                wsSessionId, userId, liveSessionId);

        // 1. Cleanup participant từ live session
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

        // 2. Cleanup pending join request nếu có
        if (userId != null && joinRequestRedisService.hasActiveRequest(userId)) {
            try {
                String requestId = joinRequestRedisService.getActiveRequestId(userId);
                JoinRequest request = joinRequestRedisService.getJoinRequest(requestId);

                if (request != null) {
                    log.info("🧹 Cleaning up pending join request {} for disconnected user {}", requestId, userId);

                    // Delete request from Redis
                    joinRequestRedisService.deleteJoinRequest(requestId, request.getSessionId(), userId);

                    // ✅ Notify owner that user disconnected (with requestId)
                    LiveSession session = liveSessionRepository.findById(request.getSessionId()).orElse(null);
                    if (session != null) {
                        Long hostId = session.getHost().getId();
                        webSocketService.sendToUser(hostId, "/queue/notification",
                                SystemNotification.builder()
                                        .type("INFO")
                                        .title("Join Request Cancelled")
                                        .message(request.getUserName() + " disconnected while waiting for approval")
                                        .requiresAction(false)
                                        .data(Map.of("requestId", requestId))
                                        .build()
                        );
                    }

                    log.info("✅ Join request {} cleaned up successfully", requestId);
                }
            } catch (Exception e) {
                log.error("❌ Failed to cleanup join request for user {}: {}", userId, e.getMessage(), e);
            }
        }

        log.info("Disconnected from websocket session {}", accessor.getSessionId());
    }

}
