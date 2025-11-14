package com.fpt.producerworkbench.service;

import com.fpt.producerworkbench.entity.LiveSession;
import com.fpt.producerworkbench.entity.SessionParticipant;
import com.fpt.producerworkbench.repository.LiveSessionRepository;
import com.fpt.producerworkbench.repository.SessionParticipantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SessionReminderScheduler {

    private final LiveSessionRepository sessionRepository;
    private final SessionParticipantRepository participantRepository;
    private final EmailService emailService;
    
    // Track sessions that have already been reminded to avoid duplicate emails
    private final Set<String> remindedSessions = ConcurrentHashMap.newKeySet();

    /**
     * Run every minute to check for sessions starting in 5 minutes
     */
    @Scheduled(cron = "0 * * * * *") // Every minute at second 0
    @Transactional(readOnly = true)
    public void sendSessionReminders() {
        try {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime fiveMinutesLater = now.plusMinutes(5);
            LocalDateTime sixMinutesLater = now.plusMinutes(6);

            // Find sessions that will start in 5-6 minutes window
            List<LiveSession> upcomingSessions = sessionRepository.findSessionsToRemind(
                    fiveMinutesLater, 
                    sixMinutesLater
            );

            log.debug("Found {} sessions starting in 5 minutes", upcomingSessions.size());

            for (LiveSession session : upcomingSessions) {
                // Skip if already reminded
                if (remindedSessions.contains(session.getId())) {
                    continue;
                }

                try {
                    sendReminderForSession(session);
                    remindedSessions.add(session.getId());
                    log.info("Reminder sent for session: {}", session.getId());
                } catch (Exception e) {
                    log.error("Failed to send reminder for session {}: {}", 
                            session.getId(), e.getMessage(), e);
                }
            }

        } catch (Exception e) {
            log.error("Error in session reminder scheduler: {}", e.getMessage(), e);
        }
    }

    private void sendReminderForSession(LiveSession session) {
        // Get all invited participants
        List<SessionParticipant> participants = participantRepository
                .findBySessionId(session.getId());

        if (participants.isEmpty()) {
            log.info("No participants to remind for session: {}", session.getId());
            return;
        }

        // Get emails of all invited participants
        List<String> participantEmails = participants.stream()
                .map(p -> p.getUser().getEmail())
                .collect(Collectors.toList());

        try {
            emailService.sendSessionReminderNotification(session, participantEmails);
            log.info("Reminder email sent to {} participants for session: {}", 
                    participantEmails.size(), session.getId());
        } catch (Exception e) {
            log.error("Failed to send reminder emails for session {}: {}", 
                    session.getId(), e.getMessage());
        }
    }

    /**
     * Clean up reminded sessions set periodically (every hour)
     * Remove sessions that have already started
     */
    @Scheduled(cron = "0 0 * * * *") // Every hour
    @Transactional(readOnly = true)
    public void cleanupRemindedSessions() {
        try {
            LocalDateTime now = LocalDateTime.now();
            
            // Remove sessions that started more than 1 hour ago
            remindedSessions.removeIf(sessionId -> {
                return sessionRepository.findById(sessionId)
                        .map(session -> {
                            LocalDateTime scheduledStart = session.getScheduledStart();
                            return scheduledStart != null && scheduledStart.isBefore(now.minusHours(1));
                        })
                        .orElse(true); // Remove if session not found
            });
            
            log.debug("Cleaned up reminded sessions set, current size: {}", remindedSessions.size());
        } catch (Exception e) {
            log.error("Error cleaning up reminded sessions: {}", e.getMessage());
        }
    }
}
