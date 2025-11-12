package com.fpt.producerworkbench.scheduler;

import com.fpt.producerworkbench.common.SessionStatus;
import com.fpt.producerworkbench.entity.LiveSession;
import com.fpt.producerworkbench.repository.LiveSessionRepository;
import com.fpt.producerworkbench.repository.SessionParticipantRepository;
import com.fpt.producerworkbench.service.WebSocketService;
import com.fpt.producerworkbench.dto.websocket.SessionStateChangeEvent;
import com.fpt.producerworkbench.dto.websocket.SystemNotification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class SessionTimeoutScheduler {

    private final LiveSessionRepository sessionRepository;
    private final SessionParticipantRepository participantRepository;
    private final WebSocketService webSocketService;

    private static final int INACTIVITY_TIMEOUT_MINUTES = 10;

    /**
     * Check for inactive ACTIVE sessions every 5 minutes
     * Auto-end sessions with no online participants for > 10 minutes
     */
    @Scheduled(fixedRate = 300000) // 5 minutes = 300,000 ms
    @Transactional
    public void checkInactiveSessions() {
        log.debug("🔍 Checking for inactive sessions...");

        try {
            // Find all ACTIVE sessions
            List<LiveSession> activeSessions = sessionRepository.findAll()
                    .stream()
                    .filter(s -> s.getStatus() == SessionStatus.ACTIVE)
                    .toList();

            if (activeSessions.isEmpty()) {
                log.debug("✅ No active sessions to check");
                return;
            }

            log.info("📊 Found {} active sessions to check for inactivity", activeSessions.size());

            for (LiveSession session : activeSessions) {
                checkAndEndInactiveSession(session);
            }

        } catch (Exception e) {
            log.error("❌ Error checking inactive sessions: {}", e.getMessage(), e);
        }
    }

    private void checkAndEndInactiveSession(LiveSession session) {
        try {
            // Check if session has online participants
            long onlineCount = participantRepository.findOnlineParticipantsBySessionId(session.getId()).size();

            if (onlineCount > 0) {
                log.debug("✅ Session {} has {} online participants, skipping", session.getId(), onlineCount);
                return;
            }

            // No online participants - check last activity time
            LocalDateTime lastActivity = session.getLastActivityTime();
            
            if (lastActivity == null) {
                // Fallback to actualStart if lastActivityTime not set
                lastActivity = session.getActualStart();
            }

            if (lastActivity == null) {
                log.warn("⚠️ Session {} has no activity time recorded, skipping", session.getId());
                return;
            }

            // Calculate inactivity duration
            LocalDateTime now = LocalDateTime.now();
            long inactiveMinutes = java.time.Duration.between(lastActivity, now).toMinutes();

            log.debug("📊 Session {} inactive for {} minutes (threshold: {})", 
                    session.getId(), inactiveMinutes, INACTIVITY_TIMEOUT_MINUTES);

            // Auto-end if inactive for > 10 minutes
            if (inactiveMinutes >= INACTIVITY_TIMEOUT_MINUTES) {
                log.info("🔴 Auto-ending session {} due to {} minutes of inactivity", 
                        session.getId(), inactiveMinutes);

                endInactiveSession(session);
            }

        } catch (Exception e) {
            log.error("❌ Error processing session {}: {}", session.getId(), e.getMessage(), e);
        }
    }

    @Transactional
    protected void endInactiveSession(LiveSession session) {
        try {
            SessionStatus oldStatus = session.getStatus();

            // Update session status
            session.setStatus(SessionStatus.ENDED);
            session.setActualEnd(LocalDateTime.now());
            session.setCurrentParticipants(0);
            sessionRepository.save(session);

            // Mark all participants as offline
            participantRepository.findBySessionId(session.getId()).forEach(p -> {
                if (p.getIsOnline()) {
                    p.markAsOffline();
                    participantRepository.save(p);
                }
            });

            // ✅ Broadcast session ended
            webSocketService.broadcastSessionStateChange(session.getId(),
                    SessionStateChangeEvent.builder()
                            .sessionId(session.getId())
                            .oldStatus(oldStatus)
                            .newStatus(SessionStatus.ENDED)
                            .triggeredBy("System")
                            .triggeredByUserId(0L)
                            .message("Session auto-ended due to inactivity")
                            .build()
            );

            // ✅ Send system notification
            webSocketService.broadcastSystemNotification(session.getId(),
                    SystemNotification.builder()
                            .type("WARNING")
                            .title("Session Auto-Ended")
                            .message("This session was automatically ended due to " + 
                                    INACTIVITY_TIMEOUT_MINUTES + " minutes of inactivity")
                            .requiresAction(false)
                            .build()
            );

            log.info("✅ Session {} auto-ended successfully", session.getId());

        } catch (Exception e) {
            log.error("❌ Failed to auto-end session {}: {}", session.getId(), e.getMessage(), e);
        }
    }
}
