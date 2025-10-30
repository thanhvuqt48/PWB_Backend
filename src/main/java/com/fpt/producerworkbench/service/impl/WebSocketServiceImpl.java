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

// broadcast SessionStateChangeEvent
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
// broadcast ParticipantEvent
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
// broadcast ChatMessage
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
// broadcast PlaybackEvent
    @Override
    public void broadcastPlaybackEvent(String sessionId, PlaybackEvent event) {
        log.debug("Broadcasting playback event: {} - {} in session {}",
                event.getAction(), event.getFileName(), sessionId);
        SessionEventMessage message = SessionEventMessage.builder()
                .eventType("PLAYBACK_" + event.getAction())
                .sessionId(sessionId)
                .timestamp(LocalDateTime.now())
                .payload(event)
                .build();
        try {
            messagingTemplate.convertAndSend("/topic/session/" + sessionId + "/playback", message);
            log.info("✅ Playback event broadcasted: {} - {}", event.getAction(), event.getFileName());
        } catch (Exception e) {
            log.error("❌ Failed to broadcast playback event: {}", e.getMessage());
        }
    }
// broadcast SystemNotification
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
//Send private message to user
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
// broadcastSessionSummary
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
}
