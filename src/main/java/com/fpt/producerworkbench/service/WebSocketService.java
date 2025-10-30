package com.fpt.producerworkbench.service;

import com.fpt.producerworkbench.dto.websocket.*;

public interface WebSocketService {

     // Broadcast session state change to all participants
    void broadcastSessionStateChange(String sessionId, SessionStateChangeEvent event);

     // Broadcast participant event to all participants
    void broadcastParticipantEvent(String sessionId, ParticipantEvent event);

     //Broadcast chat message to all participants
    void broadcastChatMessage(String sessionId, ChatMessage message);

     //Broadcast playback event to all participants
    void broadcastPlaybackEvent(String sessionId, PlaybackEvent event);

     //Send system notification to all session participants
    void broadcastSystemNotification(String sessionId, SystemNotification notification);

     //Send private message to specific user
    void sendToUser(Long userId, String destination, Object payload);

     //Send session summary to all participants (when session ends)
    void broadcastSessionSummary(String sessionId, Object summary);
}
