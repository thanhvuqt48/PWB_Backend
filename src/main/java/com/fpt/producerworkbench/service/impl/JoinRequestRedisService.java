package com.fpt.producerworkbench.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fpt.producerworkbench.dto.websocket.JoinRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class JoinRequestRedisService {

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private static final String JOIN_REQUEST_KEY = "join-request:%s";
    private static final String SESSION_PENDING_KEY = "session:%s:pending-requests";
    private static final String USER_ACTIVE_REQUEST_KEY = "user:%s:active-request";
    private static final String REQUEST_PROCESSING_LOCK = "request:%s:processing";
    private static final Duration DEFAULT_TTL = Duration.ofMinutes(5);
    private static final Duration LOCK_TTL = Duration.ofSeconds(10);

    /**
     * Lưu join request với TTL
     */
    public void saveJoinRequest(JoinRequest request) {
        try {
            String requestKey = String.format(JOIN_REQUEST_KEY, request.getRequestId());
            String sessionKey = String.format(SESSION_PENDING_KEY, request.getSessionId());
            String userKey = String.format(USER_ACTIVE_REQUEST_KEY, request.getUserId());

            String requestJson = objectMapper.writeValueAsString(request);

            // 1. Save request object
            redisTemplate.opsForValue().set(requestKey, requestJson, DEFAULT_TTL);

            // 2. Add to session's pending set
            redisTemplate.opsForSet().add(sessionKey, request.getRequestId());
            redisTemplate.expire(sessionKey, DEFAULT_TTL);

            // 3. Mark user has active request
            redisTemplate.opsForValue().set(userKey, request.getRequestId(), DEFAULT_TTL);

            log.info("✅ Saved join request {} for user {} in session {}", 
                    request.getRequestId(), request.getUserId(), request.getSessionId());

        } catch (JsonProcessingException e) {
            log.error("❌ Failed to serialize join request: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to save join request", e);
        }
    }

    /**
     * Lấy join request by ID
     */
    public JoinRequest getJoinRequest(String requestId) {
        try {
            String requestKey = String.format(JOIN_REQUEST_KEY, requestId);
            String requestJson = redisTemplate.opsForValue().get(requestKey);

            if (requestJson == null) {
                log.warn("⚠️ Join request {} not found or expired", requestId);
                return null;
            }

            JoinRequest request = objectMapper.readValue(requestJson, JoinRequest.class);
            
            // Check nếu expired
            if (request.isExpired()) {
                log.warn("⚠️ Join request {} is expired", requestId);
                deleteJoinRequest(requestId, request.getSessionId(), request.getUserId());
                return null;
            }

            return request;

        } catch (JsonProcessingException e) {
            log.error("❌ Failed to deserialize join request: {}", e.getMessage());
            
            // ✅ Clean up corrupted data
            String requestKey = String.format(JOIN_REQUEST_KEY, requestId);
            try {
                redisTemplate.delete(requestKey);
                log.info("🗑️ Deleted corrupted join request data: {}", requestId);
            } catch (Exception ex) {
                log.error("❌ Failed to delete corrupted data: {}", ex.getMessage());
            }
            
            return null;
        }
    }

    /**
     * Lấy tất cả pending requests của session
     */
    public List<JoinRequest> getPendingRequests(String sessionId) {
        String sessionKey = String.format(SESSION_PENDING_KEY, sessionId);
        Set<String> requestIds = redisTemplate.opsForSet().members(sessionKey);

        List<JoinRequest> requests = new ArrayList<>();
        if (requestIds != null) {
            for (String requestId : requestIds) {
                JoinRequest request = getJoinRequest(requestId);
                if (request != null && !request.isExpired()) {
                    requests.add(request);
                }
            }
        }

        log.debug("📋 Found {} pending requests for session {}", requests.size(), sessionId);
        return requests;
    }

    /**
     * Xóa join request
     */
    public void deleteJoinRequest(String requestId, String sessionId, Long userId) {
        String requestKey = String.format(JOIN_REQUEST_KEY, requestId);
        String sessionKey = String.format(SESSION_PENDING_KEY, sessionId);
        String userKey = String.format(USER_ACTIVE_REQUEST_KEY, userId);

        redisTemplate.delete(requestKey);
        redisTemplate.opsForSet().remove(sessionKey, requestId);
        redisTemplate.delete(userKey);

        log.info("🗑️ Deleted join request {} for user {} in session {}", requestId, userId, sessionId);
    }

    /**
     * Check user có active request không
     */
    public boolean hasActiveRequest(Long userId) {
        String userKey = String.format(USER_ACTIVE_REQUEST_KEY, userId);
        String requestId = redisTemplate.opsForValue().get(userKey);
        
        if (requestId == null) {
            return false;
        }

        // Double check request còn tồn tại không
        JoinRequest request = getJoinRequest(requestId);
        return request != null;
    }

    /**
     * Lấy active request ID của user
     */
    public String getActiveRequestId(Long userId) {
        String userKey = String.format(USER_ACTIVE_REQUEST_KEY, userId);
        return redisTemplate.opsForValue().get(userKey);
    }

    /**
     * Acquire processing lock (atomic operation để prevent double approve)
     */
    public boolean acquireProcessingLock(String requestId) {
        String lockKey = String.format(REQUEST_PROCESSING_LOCK, requestId);
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(lockKey, "processing", LOCK_TTL);
        
        if (Boolean.TRUE.equals(acquired)) {
            log.debug("🔒 Acquired processing lock for request {}", requestId);
            return true;
        } else {
            log.warn("⚠️ Failed to acquire lock for request {} - already being processed", requestId);
            return false;
        }
    }

    /**
     * Release processing lock
     */
    public void releaseProcessingLock(String requestId) {
        String lockKey = String.format(REQUEST_PROCESSING_LOCK, requestId);
        redisTemplate.delete(lockKey);
        log.debug("🔓 Released processing lock for request {}", requestId);
    }

    /**
     * Cleanup expired requests (for scheduled job)
     */
    public int cleanupExpiredRequests() {
        int cleanedCount = 0;
        
        // Scan all join-request keys
        Set<String> keys = redisTemplate.keys("join-request:*");
        
        if (keys != null) {
            for (String key : keys) {
                String requestId = key.replace("join-request:", "");
                JoinRequest request = getJoinRequest(requestId);
                
                if (request == null || request.isExpired()) {
                    if (request != null) {
                        deleteJoinRequest(requestId, request.getSessionId(), request.getUserId());
                    }
                    cleanedCount++;
                }
            }
        }
        
        if (cleanedCount > 0) {
            log.info("🧹 Cleaned up {} expired join requests", cleanedCount);
        }
        
        return cleanedCount;
    }

    /**
     * Scan all session pending keys (for scheduler)
     */
    public Set<String> scanSessionKeys() {
        Set<String> keys = redisTemplate.keys("session:*:pending-requests");
        return keys != null ? keys : Set.of();
    }
}
