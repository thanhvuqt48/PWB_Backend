package com.fpt.producerworkbench.configuration;

import com.fpt.producerworkbench.service.SessionParticipantService;
import com.fpt.producerworkbench.service.WebSocketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class WebSocketEventListener {

    private final SessionParticipantService participantService;
    private final WebSocketService webSocketService;

    @EventListener
    public void handleWebSocketConnectListener(SessionConnectedEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        Map<String, Object> sessionAttributes = headerAccessor.getSessionAttributes();
        String sessionId = headerAccessor.getSessionId();

        Long userId = sessionAttributes != null && sessionAttributes.get("userId") != null
                ? ((Number) sessionAttributes.get("userId")).longValue()
                : null;
        String liveSessionId = sessionAttributes != null && sessionAttributes.get("liveSessionId") != null
                ? sessionAttributes.get("liveSessionId").toString()
                : null;

        log.info("🔌 WebSocket CONNECTED - Session: {}, User ID: {}, LiveSession: {}",
                sessionId, userId, liveSessionId);
    }

    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String wsSessionId = headerAccessor.getSessionId();

        Map<String, Object> sessionAttributes = headerAccessor.getSessionAttributes();
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
            } catch (NumberFormatException ignored) {}
        }

        String liveSessionId = sessionAttributes.get("liveSessionId") != null
                ? sessionAttributes.get("liveSessionId").toString()
                : null;

        log.info("🔌 WebSocket DISCONNECTED - WsSession: {}, User ID: {}, LiveSession: {}",
                wsSessionId, userId, liveSessionId);

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
    }
}
