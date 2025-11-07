package com.fpt.producerworkbench.controller;

import com.fpt.producerworkbench.dto.websocket.*;
import com.fpt.producerworkbench.entity.LiveSession;
import com.fpt.producerworkbench.entity.User;
import com.fpt.producerworkbench.exception.AppException;
import com.fpt.producerworkbench.exception.ErrorCode;
import com.fpt.producerworkbench.repository.LiveSessionRepository;
import com.fpt.producerworkbench.repository.UserRepository;
import com.fpt.producerworkbench.service.JoinRequestService;
import com.fpt.producerworkbench.service.WebSocketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
@Slf4j
public class SessionWebSocketController {

    private final WebSocketService webSocketService;
    private final UserRepository userRepository;
    private final JoinRequestService joinRequestService;
    private final LiveSessionRepository sessionRepository;

    /**
     * Handle chat messages
     * Client sends to: /app/session/{sessionId}/chat
     * Server broadcasts to: /topic/session/{sessionId}/chat
     */
    @MessageMapping("/session/{sessionId}/chat")
    public void sendChatMessage(
            @DestinationVariable String sessionId,
            @Payload ChatMessage message,
            SimpMessageHeaderAccessor headerAccessor) {  // ✅ Thay Principal bằng SimpMessageHeaderAccessor

        log.info("📨 Received chat message in session {}", sessionId);

        try {
            // ✅ Get userId from session attributes (stored during CONNECT)
            Long userId = (Long) headerAccessor.getSessionAttributes().get("userId");

            if (userId == null) {
                log.warn("⚠️ Anonymous chat message in session {} - No userId in session", sessionId);
                sendAnonymousMessage(sessionId, message);
                return;
            }

            // ✅ Get user from database
            User sender = userRepository.findById(userId)
                    .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

            // Build complete message
            ChatMessage completeMessage = ChatMessage.builder()
                    .messageId(UUID.randomUUID().toString())
                    .sessionId(sessionId)
                    .senderId(sender.getId())
                    .senderName(sender.getFirstName() + " " + sender.getLastName())
                    .senderAvatarUrl(sender.getAvatarUrl())
                    .content(message.getContent())
                    .type(message.getType() != null ? message.getType() : "TEXT")
                    .timestamp(LocalDateTime.now())
                    .build();

            webSocketService.broadcastChatMessage(sessionId, completeMessage);

            log.info("✅ Chat message broadcasted from {} (ID: {})", sender.getEmail(), userId);

        } catch (Exception e) {
            log.error("❌ Error handling chat message: {}", e.getMessage(), e);
        }
    }

    /**
     * Handle playback control events
     * Client sends to: /app/session/{sessionId}/playback
     * Server broadcasts to: /topic/session/{sessionId}/playback
     */
    @MessageMapping("/session/{sessionId}/playback")
    public void controlPlayback(
            @DestinationVariable String sessionId,
            @Payload PlaybackEvent event,
            SimpMessageHeaderAccessor headerAccessor) {  // ✅ Changed

        log.info("🎵 Received playback event in session {}: {}", sessionId, event.getAction());

        try {
            Long userId = (Long) headerAccessor.getSessionAttributes().get("userId");

            if (userId == null) {
                log.warn("⚠️ Anonymous playback control in session {}", sessionId);
                event.setTriggeredByUserId(0L);
                event.setTriggeredByUserName("Anonymous");
            } else {
                User user = userRepository.findById(userId)
                        .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

                event.setTriggeredByUserId(user.getId());
                event.setTriggeredByUserName(user.getFirstName() + " " + user.getLastName());
            }

            webSocketService.broadcastPlaybackEvent(sessionId, event);
            log.info("✅ Playback event broadcasted: {}", event.getAction());

        } catch (Exception e) {
            log.error("❌ Error handling playback event: {}", e.getMessage(), e);
        }
    }

    /**
     * Handle user connection to session
     * Client sends to: /app/session/{sessionId}/connect
     */
    @MessageMapping("/session/{sessionId}/connect")
    @SendToUser("/queue/reply")
    public String handleConnect(
            @DestinationVariable String sessionId,
            SimpMessageHeaderAccessor headerAccessor,
            Principal principal) {

        // ✅ Check if principal exists
        String email = (principal != null) ? principal.getName() : "anonymous";

        // Store session ID in WebSocket session attributes
        String sessionIdAttr = (String) headerAccessor.getSessionAttributes().get("sessionId");
        if (sessionIdAttr == null) {
            headerAccessor.getSessionAttributes().put("sessionId", sessionId);
            headerAccessor.getSessionAttributes().put("userEmail", email);
        }

        log.info("🔌 User {} connected to session: {}", email, sessionId);

        return "Connected to session: " + sessionId;
    }

    /**
     * Handle user disconnection
     * Client sends to: /app/session/{sessionId}/disconnect
     */
    @MessageMapping("/session/{sessionId}/disconnect")
    public void handleDisconnect(
            @DestinationVariable String sessionId,
            Principal principal) {

        String email = (principal != null) ? principal.getName() : "anonymous";
        log.info("🔌 User {} disconnected from session: {}", email, sessionId);

        // Note: Actual disconnect is handled by @EventListener in WebSocketEventListener
    }

    /**
     * Handle typing indicator
     * Client sends to: /app/session/{sessionId}/typing
     * Server broadcasts to: /topic/session/{sessionId}/typing
     */
    @MessageMapping("/session/{sessionId}/typing")
    public void handleTyping(
            @DestinationVariable String sessionId,
            @Payload String action,
            SimpMessageHeaderAccessor headerAccessor) {  // ✅ Changed

        try {
            Long userId = (Long) headerAccessor.getSessionAttributes().get("userId");

            if (userId == null) {
                log.debug("⚠️ Anonymous typing indicator in session {}", sessionId);
                return;
            }

            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

            webSocketService.broadcastParticipantEvent(sessionId,
                    com.fpt.producerworkbench.dto.websocket.ParticipantEvent.builder()
                            .action("TYPING_" + action.toUpperCase())
                            .userId(user.getId())
                            .userName(user.getFirstName() + " " + user.getLastName())
                            .build()
            );

        } catch (Exception e) {
            log.error("❌ Error handling typing indicator: {}", e.getMessage(), e);
        }

    }

    // ========== JOIN REQUEST HANDLERS ==========

    /**
     * Member gửi request xin vào phòng
     * Client sends to: /app/session/{sessionId}/request-join
     * Server sends notification to owner: /user/{ownerId}/queue/join-requests
     */
    @MessageMapping("/session/{sessionId}/request-join")
    public void handleJoinRequest(
            @DestinationVariable String sessionId,
            SimpMessageHeaderAccessor headerAccessor) {

        log.info("🙋 Received join request for session {}", sessionId);

        try {
            // Get userId và wsSessionId từ session attributes
            Long userId = (Long) headerAccessor.getSessionAttributes().get("userId");
            String wsSessionId = headerAccessor.getSessionId();

            if (userId == null) {
                log.error("❌ Cannot process join request - userId not found in session");
                webSocketService.sendToUser(0L, "/queue/error",
                        SystemNotification.builder()
                                .type("ERROR")
                                .title("Authentication Required")
                                .message("Please authenticate to join the session")
                                .requiresAction(false)
                                .build()
                );
                return;
            }

            // Create join request (validate inside service)
            JoinRequest request = joinRequestService.createJoinRequest(sessionId, userId, wsSessionId);

            // ✅ Check if auto-approved (user has history)
            if (Boolean.TRUE.equals(request.getApproved()) && Boolean.TRUE.equals(request.getShouldCallJoinAPI())) {
                log.info("✅ User {} auto-approved (has history) - sending immediate response", userId);
                
                // ✅ Add null checks and safe defaults
                String requestId = request.getRequestId() != null ? request.getRequestId() : "auto-approved-" + userId;
                String reason = "Auto-approved - you have joined this session before";
                
                // Send immediate approval response to member
                JoinRequestResponse response = JoinRequestResponse.builder()
                        .requestId(requestId)
                        .sessionId(sessionId)
                        .userId(userId)
                        .approved(true)
                        .shouldCallJoinAPI(true)
                        .reason(reason)
                        .timestamp(LocalDateTime.now())
                        .build();
                
                log.info("✅ Sending auto-approval response: {}", response);
                
                webSocketService.sendToUser(userId, "/queue/join-response", response);
                
                log.info("✅ Auto-approval response sent to user {}", userId);
                return; // ✅ No need to notify owner
            }

            // Get session để lấy hostId
            LiveSession session = sessionRepository.findById(sessionId)
                    .orElseThrow(() -> new AppException(ErrorCode.SESSION_NOT_FOUND));

            Long hostId = session.getHost().getId();

            // Send notification to owner
            webSocketService.sendToUser(hostId, "/queue/join-requests",
                    JoinRequestNotification.from(request)
            );

            // Confirm to member
            webSocketService.sendToUser(userId, "/queue/request-sent",
                    SystemNotification.builder()
                            .type("INFO")
                            .title("Request Sent")
                            .message("Your join request has been sent to the host. Waiting for approval...")
                            .requiresAction(false)
                            .build()
            );

            log.info("✅ Join request {} sent to host {} for user {}", request.getRequestId(), hostId, userId);

        } catch (AppException e) {
            log.error("❌ Error creating join request: {}", e.getMessage());
            Long userId = (Long) headerAccessor.getSessionAttributes().get("userId");
            if (userId != null) {
                webSocketService.sendToUser(userId, "/queue/error",
                        SystemNotification.builder()
                                .type("ERROR")
                                .title("Join Request Failed")
                                .message(e.getMessage())
                                .requiresAction(false)
                                .build()
                );
            }
        }
    }

    /**
     * Owner approve join request
     * Client sends to: /app/session/{sessionId}/approve-join
     * Server sends response to member: /user/{memberId}/queue/join-response
     */
    @MessageMapping("/session/{sessionId}/approve-join")
    public void handleApproveJoin(
            @DestinationVariable String sessionId,
            @Payload ApproveJoinRequest payload,
            SimpMessageHeaderAccessor headerAccessor) {

        log.info("✅ Received approve request for {} in session {}", payload.getRequestId(), sessionId);

        try {
            Long approverId = (Long) headerAccessor.getSessionAttributes().get("userId");

            if (approverId == null) {
                log.error("❌ Cannot approve - approverId not found");
                return;
            }

            // Approve request (validate inside service)
            JoinRequest approvedRequest = joinRequestService.approveJoinRequest(payload.getRequestId(), approverId);

            // Send approval response to member
            webSocketService.sendToUser(approvedRequest.getUserId(), "/queue/join-response",
                    JoinRequestResponse.approved(approvedRequest.getRequestId(), sessionId)
            );

            // Send confirmation to owner
            webSocketService.sendToUser(approverId, "/queue/notification",
                    SystemNotification.builder()
                            .type("SUCCESS")
                            .title("Request Approved")
                            .message("You approved " + approvedRequest.getUserName() + " to join the session")
                            .requiresAction(false)
                            .build()
            );

            log.info("✅ Join request {} approved by {}", payload.getRequestId(), approverId);

        } catch (AppException e) {
            log.error("❌ Error approving join request: {}", e.getMessage());
            Long approverId = (Long) headerAccessor.getSessionAttributes().get("userId");
            if (approverId != null) {
                webSocketService.sendToUser(approverId, "/queue/error",
                        SystemNotification.builder()
                                .type("ERROR")
                                .title("Approval Failed")
                                .message(e.getMessage())
                                .requiresAction(false)
                                .build()
                );
            }
        }
    }

    /**
     * Owner reject join request
     * Client sends to: /app/session/{sessionId}/reject-join
     * Server sends response to member: /user/{memberId}/queue/join-response
     */
    @MessageMapping("/session/{sessionId}/reject-join")
    public void handleRejectJoin(
            @DestinationVariable String sessionId,
            @Payload RejectJoinRequest payload,
            SimpMessageHeaderAccessor headerAccessor) {

        log.info("❌ Received reject request for {} in session {}", payload.getRequestId(), sessionId);

        try {
            Long approverId = (Long) headerAccessor.getSessionAttributes().get("userId");

            if (approverId == null) {
                log.error("❌ Cannot reject - approverId not found");
                return;
            }

            // Reject request (returns request info before deletion)
            JoinRequest rejectedRequest = joinRequestService.rejectJoinRequest(
                    payload.getRequestId(), 
                    approverId, 
                    payload.getReason()
            );

            // Send rejection response to member
            webSocketService.sendToUser(rejectedRequest.getUserId(), "/queue/join-response",
                    JoinRequestResponse.rejected(
                            rejectedRequest.getRequestId(),
                            sessionId,
                            payload.getReason() != null ? payload.getReason() : "Host declined your request"
                    )
            );

            // Send confirmation to owner
            webSocketService.sendToUser(approverId, "/queue/notification",
                    SystemNotification.builder()
                            .type("INFO")
                            .title("Request Rejected")
                            .message("You rejected " + rejectedRequest.getUserName() + "'s join request")
                            .requiresAction(false)
                            .build()
            );

            log.info("✅ Join request {} rejected by {}", payload.getRequestId(), approverId);

        } catch (AppException e) {
            log.error("❌ Error rejecting join request: {}", e.getMessage());
            Long approverId = (Long) headerAccessor.getSessionAttributes().get("userId");
            if (approverId != null) {
                webSocketService.sendToUser(approverId, "/queue/error",
                        SystemNotification.builder()
                                .type("ERROR")
                                .title("Rejection Failed")
                                .message(e.getMessage())
                                .requiresAction(false)
                                .build()
                );
            }
        }
    }

    /**
     * Member cancel join request
     * Client sends to: /app/session/{sessionId}/cancel-request
     */
    @MessageMapping("/session/{sessionId}/cancel-request")
    public void handleCancelRequest(
            @DestinationVariable String sessionId,
            @Payload String requestId,
            SimpMessageHeaderAccessor headerAccessor) {

        log.info("🚫 Received cancel request for {} in session {}", requestId, sessionId);

        try {
            Long userId = (Long) headerAccessor.getSessionAttributes().get("userId");

            if (userId == null) {
                log.error("❌ Cannot cancel - userId not found");
                return;
            }

            // Get request info before cancelling
            JoinRequest request = joinRequestService.getJoinRequestFromRedis(requestId);
            
            if (request == null) {
                log.warn("⚠️ Join request {} not found or already processed", requestId);
                return;
            }

            // Cancel request
            joinRequestService.cancelJoinRequest(requestId, userId);

            // Send confirmation to member
            webSocketService.sendToUser(userId, "/queue/notification",
                    SystemNotification.builder()
                            .type("INFO")
                            .title("Request Cancelled")
                            .message("Your join request has been cancelled")
                            .requiresAction(false)
                            .build()
            );

            // ✅ Notify owner that request was cancelled
            LiveSession session = sessionRepository.findById(sessionId)
                    .orElse(null);
            
            if (session != null && session.getHost() != null) {
                Long ownerId = session.getHost().getId();
                webSocketService.sendToUser(ownerId, "/queue/notification",
                        SystemNotification.builder()
                                .type("INFO")
                                .title("Request Cancelled")
                                .message(request.getUserName() + " has cancelled their join request")
                                .requiresAction(false)
                                .data(Map.of("requestId", requestId))
                                .build()
                );
                log.info("📤 Notified owner {} about cancelled request", ownerId);
            }

            log.info("✅ Join request {} cancelled by user {}", requestId, userId);

        } catch (AppException e) {
            log.error("❌ Error cancelling join request: {}", e.getMessage());
        }
    }

    /**
     * Get pending requests (optional - có thể dùng REST API thay vì WebSocket)
     */
    @MessageMapping("/session/{sessionId}/get-pending-requests")
    @SendToUser("/queue/pending-requests")
    public List<JoinRequestNotification> getPendingRequests(
            @DestinationVariable String sessionId,
            SimpMessageHeaderAccessor headerAccessor) {

        Long ownerId = (Long) headerAccessor.getSessionAttributes().get("userId");

        if (ownerId == null) {
            return List.of();
        }

        List<JoinRequest> requests = joinRequestService.getPendingRequests(sessionId, ownerId);

        return requests.stream()
                .map(JoinRequestNotification::from)
                .toList();
    }

    // ========== PRIVATE HELPERS ==========

    private void sendAnonymousMessage(String sessionId, ChatMessage message) {
        ChatMessage anonymousMessage = ChatMessage.builder()
                .messageId(UUID.randomUUID().toString())
                .sessionId(sessionId)
                .senderId(0L)
                .senderName("Anonymous")
                .senderAvatarUrl(null)
                .content(message.getContent())
                .type(message.getType() != null ? message.getType() : "TEXT")
                .timestamp(LocalDateTime.now())
                .build();

        webSocketService.broadcastChatMessage(sessionId, anonymousMessage);
    }
}
