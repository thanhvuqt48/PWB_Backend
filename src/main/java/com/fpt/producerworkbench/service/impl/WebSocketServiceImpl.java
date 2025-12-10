package com.fpt.producerworkbench.service.impl;

import com.fpt.producerworkbench.dto.websocket.*;
import com.fpt.producerworkbench.service.WebSocketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class WebSocketServiceImpl implements WebSocketService {

    private final SimpMessagingTemplate messagingTemplate;

    @Override
    public void broadcastSessionStateChange(String sessionId, SessionStateChangeEvent event) {
        log.debug("Broadcasting session state change: {} -> {} for session {}",
                event.getOldStatus(), event.getNewStatus(), sessionId);
        SessionEventMessage message = SessionEventMessage.builder()
                .eventType("SESSION_STATE_CHANGED")
                .sessionId(sessionId)
                .timestamp(LocalDateTime.now())
                .payload(event)
                .build();

        try {
            messagingTemplate.convertAndSend("/topic/session/" + sessionId, message);
            log.info("✅ Session state change broadcasted: {}", event.getNewStatus());
        } catch (Exception e) {
            log.error("❌ Failed to broadcast session state change: {}", e.getMessage());
        }
    }

    @Override
    public void broadcastParticipantEvent(String sessionId, ParticipantEvent event) {
        log.debug("Broadcasting participant event: {} - {} in session {}",
                event.getAction(), event.getUserName(), sessionId);
        SessionEventMessage message = SessionEventMessage.builder()
                .eventType("PARTICIPANT_" + event.getAction())
                .sessionId(sessionId)
                .timestamp(LocalDateTime.now())
                .payload(event)
                .build();
        try {
            messagingTemplate.convertAndSend("/topic/session/" + sessionId, message);
            log.info("✅ Participant event broadcasted: {} - {}", event.getAction(), event.getUserName());
        } catch (Exception e) {
            log.error("❌ Failed to broadcast participant event: {}", e.getMessage());
        }
    }

    @Override
    public void broadcastChatMessage(String sessionId, ChatMessage chatMessage) {
        log.debug("Broadcasting chat message from {} in session {}",
                chatMessage.getSenderName(), sessionId);
        SessionEventMessage message = SessionEventMessage.builder()
                .eventType("CHAT_MESSAGE")
                .sessionId(sessionId)
                .timestamp(LocalDateTime.now())
                .payload(chatMessage)
                .build();
        try {
            messagingTemplate.convertAndSend("/topic/session/" + sessionId + "/chat", message);
            log.info("✅ Chat message broadcasted from: {}", chatMessage.getSenderName());
        } catch (Exception e) {
            log.error("❌ Failed to broadcast chat message: {}", e.getMessage());
        }
    }

    @Override
    public void broadcastPlaybackEvent(String sessionId, PlaybackEvent event) {
        String destination = "/topic/session/" + sessionId + "/playback";
        log.info("🎵 Broadcasting playback event to destination: {}", destination);
        log.info("🎵 Event details: action={}, triggeredBy={}, fileName={}", 
                event.getAction(), event.getTriggeredByUserId(), event.getFileName());
        
        SessionEventMessage message = SessionEventMessage.builder()
                .eventType("PLAYBACK_" + event.getAction())
                .sessionId(sessionId)
                .timestamp(LocalDateTime.now())
                .payload(event)
                .build();
        try {
            messagingTemplate.convertAndSend(destination, message);
            log.info("✅ Playback event broadcasted to {} : {} - {}", destination, event.getAction(), event.getFileName());
        } catch (Exception e) {
            log.error("❌ Failed to broadcast playback event: {}", e.getMessage(), e);
        }
    }

    @Override
    public void broadcastSystemNotification(String sessionId, SystemNotification notification) {
        log.debug("Broadcasting system notification: {} in session {}",
                notification.getTitle(), sessionId);
        SessionEventMessage message = SessionEventMessage.builder()
                .eventType("SYSTEM_NOTIFICATION")
                .sessionId(sessionId)
                .timestamp(LocalDateTime.now())
                .payload(notification)
                .build();
        try {
            messagingTemplate.convertAndSend("/topic/session/" + sessionId + "/notification", message);
            log.info("✅ System notification broadcasted: {}", notification.getTitle());
        } catch (Exception e) {
            log.error("❌ Failed to broadcast system notification: {}", e.getMessage());
        }
    }

    @Override
    public void sendToUser(Long userId, String destination, Object payload) {
        log.debug("Sending private message to user: {} at {}", userId, destination);
        try {
            messagingTemplate.convertAndSendToUser(
                    userId.toString(),
                    destination,
                    payload
            );
            log.info("✅ Private message sent to user: {}", userId);
        } catch (Exception e) {
            log.error("❌ Failed to send private message to user {}: {}", userId, e.getMessage());
        }
    }

    @Override
    public void broadcastSessionSummary(String sessionId, Object summary) {
        log.debug("Broadcasting session summary for session {}", sessionId);
        SessionEventMessage message = SessionEventMessage.builder()
                .eventType("SESSION_SUMMARY")
                .sessionId(sessionId)
                .timestamp(LocalDateTime.now())
                .payload(summary)
                .build();
        try {
            messagingTemplate.convertAndSend("/topic/session/" + sessionId + "/summary", message);
            log.info("✅ Session summary broadcasted for session: {}", sessionId);
        } catch (Exception e) {
            log.error("❌ Failed to broadcast session summary: {}", e.getMessage());
        }
    }

    @Override
    public void broadcastUserStatusChange(String userEmail, boolean isOnline, String conversationId) {
        log.debug("Broadcasting user status change: {} is {} in conversation {}",
                userEmail, isOnline ? "online" : "offline", conversationId);

        SessionEventMessage message = SessionEventMessage.builder()
                .eventType("USER_STATUS_CHANGE")
                .sessionId(conversationId)
                .timestamp(LocalDateTime.now())
                .payload(new UserStatusChangePayload(userEmail, isOnline))
                .build();

        try {
            messagingTemplate.convertAndSend("/topic/conversation/" + conversationId + "/status", message);
            log.info("✅ User status change broadcasted: {} is {} in conversation {}",
                    userEmail, isOnline ? "online" : "offline", conversationId);
        } catch (Exception e) {
            log.error("❌ Failed to broadcast user status change: {}", e.getMessage());
        }
    }

    @Override
    public void broadcastTrackNoteEvent(String sessionId, TrackNoteEvent event) {
        log.debug("Broadcasting track note event: {} for track {} in session {}",
                event.getAction(), event.getTrackId(), sessionId);
        SessionEventMessage message = SessionEventMessage.builder()
                .eventType("TRACK_NOTE_" + event.getAction())
                .sessionId(sessionId)
                .timestamp(LocalDateTime.now())
                .payload(event)
                .build();
        try {
            messagingTemplate.convertAndSend("/topic/session/" + sessionId + "/notes", message);
            log.info("✅ Track note event broadcasted: {} for track {}", event.getAction(), event.getTrackId());
        } catch (Exception e) {
            log.error("❌ Failed to broadcast track note event: {}", e.getMessage());
        }
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    private static class UserStatusChangePayload {
        private String userEmail;
        private boolean isOnline;
    }
}
