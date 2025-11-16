package com.fpt.producerworkbench.service.impl;

import com.fpt.producerworkbench.entity.WebSocketSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j(topic = "WEB-SOCKET-SESSION-SERVICE")
public class WebSocketSessionRedisService {

    private static final String SESSION_KEY_PREFIX = "websocket_session:";
    private static final String USER_SESSIONS_KEY_PREFIX = "user:sessions:";
    private static final Duration SESSION_TIMEOUT = Duration.ofHours(24);

    private final RedisTemplate<String, Object> redisTemplate;

    public void saveWebSocketSession(WebSocketSession session) {
        Assert.notNull(session, "WebSocketSession must not be null");
        Assert.hasText(session.getUserId().toString(), "UserId must not be empty");
        Assert.hasText(session.getSocketSessionId(), "SocketSessionId must not be empty");

        String userId = session.getUserId();
        String socketSessionId = session.getSocketSessionId();
        String sessionKey = SESSION_KEY_PREFIX + socketSessionId;
        String userSessionsKey = USER_SESSIONS_KEY_PREFIX + userId;

        try {
            redisTemplate.opsForValue().set(sessionKey, userId, SESSION_TIMEOUT);
            log.debug("Saved session mapping to key: {}, value: {}", sessionKey, userId);

            Long added = redisTemplate.opsForSet().add(userSessionsKey, socketSessionId);
            if (added != null && added > 0) {
                redisTemplate.expire(userSessionsKey, SESSION_TIMEOUT);
                log.info("Saved WebSocket session - UserId: {}, SessionId: {}", userId, socketSessionId);
            } else {
                log.warn("Session ID {} already exists for user {}", socketSessionId, userId);
            }
        } catch (Exception e) {
            log.error("Failed to save WebSocket session for UserId: {}, SessionId: {}. Error: {}",
                    userId, socketSessionId, e.getMessage());
        }
    }

    public void deleteWebsocketSession(String websocketSessionId) {
        Assert.hasText(websocketSessionId, "WebSocketSessionId must not be empty");

        try {
            String sessionKey = SESSION_KEY_PREFIX + websocketSessionId;
            Object userIdObj = redisTemplate.opsForValue().get(sessionKey);

            if (userIdObj != null) {
                String userId = userIdObj.toString();
                redisTemplate.delete(sessionKey);
                log.debug("Deleted session from key: {}", sessionKey);

                String userSessionsKey = USER_SESSIONS_KEY_PREFIX + userId;
                redisTemplate.opsForSet().remove(userSessionsKey, websocketSessionId);
                log.info("Deleted WebSocket session - SessionId: {}", websocketSessionId);
            } else {
                log.warn("No session found for SessionId: {}", websocketSessionId);
            }
        } catch (Exception e) {
            log.error("Failed to delete WebSocket session for SessionId: {}. Error: {}",
                    websocketSessionId, e.getMessage());
        }
    }

    public Set<WebSocketSession> getSessionByUserIds(Set<String> userIds) {
        if(userIds == null || userIds.isEmpty()) {
            return new HashSet<>();
        }
        Set<WebSocketSession> sessions = new HashSet<>();
        userIds.forEach(userId -> {
            String userIdKey = USER_SESSIONS_KEY_PREFIX + userId;
            Set<Object> sessionIds = redisTemplate.opsForSet().members(userIdKey);

            if(sessionIds != null && !sessionIds.isEmpty()) {
                log.debug("Found {} session IDs for user {}: {}", sessionIds.size(), userId, sessionIds);
                sessionIds.forEach(sessionId -> {
                    // Verify that the session actually exists in Redis
                    String sessionKey = SESSION_KEY_PREFIX + sessionId.toString();
                    Object sessionUserId = redisTemplate.opsForValue().get(sessionKey);

                    if(sessionUserId != null) {
                        WebSocketSession session = WebSocketSession.builder()
                                .socketSessionId(sessionId.toString())
                                .userId(userId)
                                .build();
                        sessions.add(session);
                        log.debug("Valid session found: {} for user {}", sessionId, userId);
                    } else {
                        // Session key doesn't exist, clean up the orphaned entry
                        log.warn("Orphaned session ID {} found for user {}, cleaning up", sessionId, userId);
                        redisTemplate.opsForSet().remove(userIdKey, sessionId);
                    }
                });
            } else {
                log.debug("No sessions found for user {}", userId);
            }
        });

        log.info("Retrieved {} active sessions for {} users", sessions.size(), userIds.size());
        return sessions;
    }

}