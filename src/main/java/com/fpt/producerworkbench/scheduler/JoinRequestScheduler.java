package com.fpt.producerworkbench.scheduler;

import com.fpt.producerworkbench.dto.websocket.JoinRequest;
import com.fpt.producerworkbench.dto.websocket.JoinRequestResponse;
import com.fpt.producerworkbench.dto.websocket.SystemNotification;
import com.fpt.producerworkbench.entity.LiveSession;
import com.fpt.producerworkbench.repository.LiveSessionRepository;
import com.fpt.producerworkbench.service.WebSocketService;
import com.fpt.producerworkbench.service.impl.JoinRequestRedisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
public class JoinRequestScheduler {

    private final JoinRequestRedisService redisService;
    private final WebSocketService webSocketService;
    private final LiveSessionRepository sessionRepository;

    /**
     * Cleanup expired requests mỗi phút
     * Redis TTL tự động xóa, job này chỉ để send notifications
     */
    @Scheduled(fixedRate = 60000) // 1 phút
    public void cleanupExpiredRequests() {
        try {
            int cleanedCount = redisService.cleanupExpiredRequests();
            if (cleanedCount > 0) {
                log.info("🧹 Scheduled cleanup: {} expired join requests", cleanedCount);
            }
        } catch (Exception e) {
            log.error("❌ Error in scheduled cleanup: {}", e.getMessage(), e);
        }
    }

    /**
     * Notify về requests sắp expire (30 giây/lần)
     * Gửi warning khi còn < 1 phút
     */
    @Scheduled(fixedRate = 30000) // 30 giây
    public void notifyUpcomingExpiry() {
        try {
            // Scan all session pending keys
            Set<String> sessionKeys = redisService.scanSessionKeys();

            for (String sessionKey : sessionKeys) {
                String sessionId = extractSessionId(sessionKey);
                List<JoinRequest> requests = redisService.getPendingRequests(sessionId);

                for (JoinRequest request : requests) {
                    long secondsRemaining = Duration.between(
                            LocalDateTime.now(),
                            request.getExpiresAt()
                    ).getSeconds();

                    // Nếu còn < 60 giây và chưa notify
                    if (secondsRemaining > 0 && secondsRemaining < 60) {
                        notifyExpiringRequest(request, secondsRemaining);
                    }
                    
                    // Nếu đã expired, send expired notification
                    else if (secondsRemaining <= 0) {
                        notifyExpiredRequest(request);
                    }
                }
            }
        } catch (Exception e) {
            log.error("❌ Error in expiry notification: {}", e.getMessage(), e);
        }
    }

    private void notifyExpiringRequest(JoinRequest request, long secondsRemaining) {
        // Notify member
        webSocketService.sendToUser(request.getUserId(), "/queue/notification",
                SystemNotification.builder()
                        .type("WARNING")
                        .title("Request Expiring Soon")
                        .message("Your join request will expire in " + secondsRemaining + " seconds")
                        .requiresAction(false)
                        .build()
        );

        // Notify owner
        LiveSession session = sessionRepository.findById(request.getSessionId()).orElse(null);
        if (session != null) {
            Long hostId = session.getHost().getId();
            webSocketService.sendToUser(hostId, "/queue/notification",
                    SystemNotification.builder()
                            .type("WARNING")
                            .title("Pending Request")
                            .message(request.getUserName() + "'s request will expire in " + secondsRemaining + " seconds")
                            .requiresAction(true)
                            .build()
            );
        }

        log.debug("⏰ Notified expiring request {} - {} seconds remaining", request.getRequestId(), secondsRemaining);
    }

    private void notifyExpiredRequest(JoinRequest request) {
        // Notify member
        webSocketService.sendToUser(request.getUserId(), "/queue/join-response",
                JoinRequestResponse.expired(request.getRequestId(), request.getSessionId())
        );

        // ✅ Notify owner with requestId in data
        LiveSession session = sessionRepository.findById(request.getSessionId()).orElse(null);
        if (session != null) {
            Long hostId = session.getHost().getId();
            webSocketService.sendToUser(hostId, "/queue/notification",
                    SystemNotification.builder()
                            .type("INFO")
                            .title("Request Expired")
                            .message(request.getUserName() + "'s join request has expired")
                            .requiresAction(false)
                            .data(Map.of("requestId", request.getRequestId()))
                            .build()
            );
        }

        // Cleanup from Redis
        redisService.deleteJoinRequest(request.getRequestId(), request.getSessionId(), request.getUserId());

        log.info("⏰ Join request {} expired and cleaned up", request.getRequestId());
    }

    private String extractSessionId(String sessionKey) {
        // Extract from "session:{sessionId}:pending-requests"
        return sessionKey.replace("session:", "").replace(":pending-requests", "");
    }
}
