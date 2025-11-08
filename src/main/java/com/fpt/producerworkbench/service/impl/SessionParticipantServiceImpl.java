package com.fpt.producerworkbench.service.impl;

import com.fpt.producerworkbench.common.InvitationStatus;
import com.fpt.producerworkbench.common.ParticipantRole;
import com.fpt.producerworkbench.common.ProjectRole;
import com.fpt.producerworkbench.common.SessionStatus;
import com.fpt.producerworkbench.configuration.AgoraConfig;
import com.fpt.producerworkbench.dto.request.InviteParticipantRequest;
import com.fpt.producerworkbench.dto.request.UpdateParticipantPermissionRequest;
import com.fpt.producerworkbench.dto.response.JoinSessionResponse;
import com.fpt.producerworkbench.dto.response.SessionParticipantResponse;
import com.fpt.producerworkbench.dto.websocket.ParticipantEvent;
import com.fpt.producerworkbench.dto.websocket.SystemNotification;
import com.fpt.producerworkbench.entity.LiveSession;
import com.fpt.producerworkbench.entity.ProjectMember;
import com.fpt.producerworkbench.entity.SessionParticipant;
import com.fpt.producerworkbench.entity.User;
import com.fpt.producerworkbench.exception.AppException;
import com.fpt.producerworkbench.exception.ErrorCode;
import com.fpt.producerworkbench.mapper.SessionParticipantMapper;
import com.fpt.producerworkbench.repository.LiveSessionRepository;
import com.fpt.producerworkbench.repository.ProjectMemberRepository;
import com.fpt.producerworkbench.repository.SessionParticipantRepository;
import com.fpt.producerworkbench.repository.UserRepository;
import com.fpt.producerworkbench.service.AgoraTokenService;
import com.fpt.producerworkbench.service.SessionParticipantService;
import com.fpt.producerworkbench.service.WebSocketService;
import io.agora.media.RtcTokenBuilder2;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class SessionParticipantServiceImpl implements SessionParticipantService {

    private final SessionParticipantRepository participantRepository;
    private final LiveSessionRepository sessionRepository;
    private final UserRepository userRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final AgoraTokenService agoraTokenService;
    private final SessionParticipantMapper participantMapper;
    private final WebSocketService webSocketService; // ✅ Add WebSocket service
    private static final int TOKEN_EXPIRATION_SECONDS = 86400; // 24 hours

    @Override
    @Transactional
    public SessionParticipantResponse inviteParticipant(
            String sessionId,
            InviteParticipantRequest request,
            Long invitedBy) {

        log.info("Inviting user {} to session {} by user {}", request.getUserId(), sessionId, invitedBy);

        LiveSession session = validateSession(sessionId);
        validateIsHost(session, invitedBy);

        User invitee = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        if (participantRepository.existsBySessionIdAndUserId(sessionId, request.getUserId())) {
            throw new AppException(ErrorCode.USER_ALREADY_INVITED);
        }

        ParticipantRole role = determineParticipantRole(session.getProject().getId(), request.getUserId());

        SessionParticipant participant = SessionParticipant.builder()
                .session(session)
                .user(invitee)
                .participantRole(role)
                .invitationStatus(InvitationStatus.PENDING)
                .invitedAt(LocalDateTime.now())
                .canShareAudio(true)
                .canShareVideo(true)
                .canControlPlayback(role == ParticipantRole.OWNER)
                .canApproveFiles(role == ParticipantRole.OWNER)
                .audioEnabled(true)
                .videoEnabled(true)
                .isOnline(false)
                .build();

        SessionParticipant saved = participantRepository.save(participant);

        // ✅ Send private notification to invited user
        webSocketService.sendToUser(request.getUserId(), "/queue/invitation",
                SystemNotification.builder()
                        .type("INFO")
                        .title("Session Invitation")
                        .message("You've been invited to join: " + session.getTitle())
                        .requiresAction(true)
                        .actionUrl("/sessions/" + sessionId + "/join")
                        .build()
        );

        log.info("User {} invited to session {} with role {}", request.getUserId(), sessionId, role);

        return participantMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public JoinSessionResponse joinSession(String sessionId, Long userId) {
        log.info("User {} joining session {}", userId, sessionId);

        LiveSession session = validateSession(sessionId);

        if (session.getStatus() != SessionStatus.ACTIVE) {
            throw new AppException(ErrorCode.SESSION_NOT_ACTIVE);
        }

        // ✅ Check if user has SessionParticipant (invited) or is host
        SessionParticipant participant = participantRepository
                .findBySessionIdAndUserId(sessionId, userId)
                .orElse(null);

        // ✅ NEW LOGIC: If session is PUBLIC → allow anyone to join (create participant automatically)
        // If session is PRIVATE → only host or invited members can join
        if (participant == null) {
            // Host can always join
            if (session.isHost(userId)) {
                participant = createParticipantForUser(session, userId);
            }
            // Public session → create participant for anyone
            else if (Boolean.TRUE.equals(session.getIsPublic())) {
                participant = createParticipantForUser(session, userId);
                // Auto-accept for public sessions
                participant.setInvitationStatus(InvitationStatus.ACCEPTED);
                participantRepository.save(participant);
            }
            // Private session + not invited → reject
            else {
                throw new AppException(ErrorCode.MUST_REQUEST_JOIN_FIRST);
            }
        }

        // If participant exists but declined → cannot join
        if (participant != null && participant.getInvitationStatus() == InvitationStatus.DECLINED) {
            throw new AppException(ErrorCode.INVITATION_DECLINED);
        }

        int agoraUid = generateAgoraUid(userId);

        // ✅ Use RtcTokenBuilder2.Role instead of just Role
        RtcTokenBuilder2.Role agoraRole = participant.canPublish()
                ? RtcTokenBuilder2.Role.ROLE_PUBLISHER
                : RtcTokenBuilder2.Role.ROLE_SUBSCRIBER;

        String agoraToken = agoraTokenService.generateRtcToken(
                session.getAgoraChannelName(),
                agoraUid,
                agoraRole,
                TOKEN_EXPIRATION_SECONDS
        );

        participant.setAgoraUid(agoraUid);
        participant.setAgoraToken(agoraToken);
        participant.setAgoraTokenExpiresAt(LocalDateTime.now().plusSeconds(TOKEN_EXPIRATION_SECONDS));
        participant.setInvitationStatus(InvitationStatus.ACCEPTED);
        participant.markAsOnline();

        participantRepository.save(participant);

        session.incrementParticipants();
        sessionRepository.save(session);

        // WebSocket broadcasts...
        webSocketService.broadcastParticipantEvent(sessionId,
                ParticipantEvent.builder()
                        .action("JOINED")
                        .userId(userId)
                        .userName(getFullName(participant.getUser()))
                        .userAvatarUrl(participant.getUser().getAvatarUrl())
                        .role(participant.getParticipantRole())
                        .isOnline(true)
                        .audioEnabled(participant.getAudioEnabled())
                        .videoEnabled(participant.getVideoEnabled())
                        .currentParticipants(session.getCurrentParticipants())
                        .build()
        );

        webSocketService.broadcastSystemNotification(sessionId,
                SystemNotification.builder()
                        .type("INFO")
                        .title("Participant Joined")
                        .message(getFullName(participant.getUser()) + " joined the session")
                        .requiresAction(false)
                        .build()
        );

        log.info("User {} joined session {} successfully with UID {}", userId, sessionId, agoraUid);

        return JoinSessionResponse.builder()
                .sessionId(sessionId)
                .channelName(session.getAgoraChannelName())
                .appId(session.getAgoraAppId())
                .token(agoraToken)
                .uid(agoraUid)
                .role(agoraRole.name())
                .expiresIn(TOKEN_EXPIRATION_SECONDS)
                .sessionTitle(session.getTitle())
                .currentParticipants(session.getCurrentParticipants())
                .build();
    }

    @Override
    @Transactional
    public void leaveSession(String sessionId, Long userId) {
        log.info("User {} leaving session {}", userId, sessionId);

        SessionParticipant participant = participantRepository
                .findBySessionIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new AppException(ErrorCode.PARTICIPANT_NOT_FOUND));

        participant.markAsOffline();
        participantRepository.save(participant);

        LiveSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new AppException(ErrorCode.SESSION_NOT_FOUND));

        session.decrementParticipants();
        sessionRepository.save(session);

        // ✅ Broadcast participant left
        webSocketService.broadcastParticipantEvent(sessionId,
                ParticipantEvent.builder()
                        .action("LEFT")
                        .userId(userId)
                        .userName(getFullName(participant.getUser()))
                        .userAvatarUrl(participant.getUser().getAvatarUrl())
                        .role(participant.getParticipantRole())
                        .isOnline(false)
                        .currentParticipants(session.getCurrentParticipants())
                        .build()
        );

        // ✅ Send system notification
        webSocketService.broadcastSystemNotification(sessionId,
                SystemNotification.builder()
                        .type("INFO")
                        .title("Participant Left")
                        .message(getFullName(participant.getUser()) + " left the session")
                        .requiresAction(false)
                        .build()
        );

        log.info("User {} left session {}", userId, sessionId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SessionParticipantResponse> getSessionParticipants(String sessionId) {
        log.debug("Getting participants for session {}", sessionId);

        List<SessionParticipant> participants = participantRepository.findBySessionId(sessionId);
        return participantMapper.toResponseList(participants);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SessionParticipantResponse> getOnlineParticipants(String sessionId) {
        log.debug("Getting online participants for session {}", sessionId);

        List<SessionParticipant> participants = participantRepository
                .findOnlineParticipantsBySessionId(sessionId);

        return participantMapper.toResponseList(participants);
    }

    @Override
    @Transactional
    public SessionParticipantResponse updateParticipantPermissions(
            String sessionId,
            Long userId,
            UpdateParticipantPermissionRequest request,
            Long updatedBy) {

        log.info("Updating permissions for user {} in session {} by {}", userId, sessionId, updatedBy);

        LiveSession session = validateSession(sessionId);
        validateIsHost(session, updatedBy);

        SessionParticipant participant = participantRepository
                .findBySessionIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new AppException(ErrorCode.PARTICIPANT_NOT_FOUND));

        if (request.getCanShareAudio() != null) {
            participant.setCanShareAudio(request.getCanShareAudio());
        }
        if (request.getCanShareVideo() != null) {
            participant.setCanShareVideo(request.getCanShareVideo());
        }
        if (request.getCanControlPlayback() != null) {
            participant.setCanControlPlayback(request.getCanControlPlayback());
        }
        if (request.getCanApproveFiles() != null) {
            participant.setCanApproveFiles(request.getCanApproveFiles());
        }

        SessionParticipant updated = participantRepository.save(participant);

        // ✅ Broadcast permissions updated
        webSocketService.broadcastParticipantEvent(sessionId,
                ParticipantEvent.builder()
                        .action("PERMISSIONS_UPDATED")
                        .userId(userId)
                        .userName(getFullName(participant.getUser()))
                        .role(participant.getParticipantRole())
                        .isOnline(participant.getIsOnline())
                        .audioEnabled(participant.getAudioEnabled())
                        .videoEnabled(participant.getVideoEnabled())
                        .currentParticipants(session.getCurrentParticipants())
                        .build()
        );

        // ✅ Send private notification to user
        webSocketService.sendToUser(userId, "/queue/notification",
                SystemNotification.builder()
                        .type("WARNING")
                        .title("Permissions Updated")
                        .message("Your session permissions have been updated by the host")
                        .requiresAction(false)
                        .build()
        );

        log.info("Permissions updated for user {} in session {}", userId, sessionId);

        return participantMapper.toResponse(updated);
    }

    @Override
    @Transactional
    public String refreshAgoraToken(String sessionId, Long userId) {
        log.info("Refreshing Agora token for user {} in session {}", userId, sessionId);

        SessionParticipant participant = participantRepository
                .findBySessionIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new AppException(ErrorCode.PARTICIPANT_NOT_FOUND));

        LiveSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new AppException(ErrorCode.SESSION_NOT_FOUND));

        // ✅ Use RtcTokenBuilder2.Role
        RtcTokenBuilder2.Role agoraRole = participant.canPublish()
                ? RtcTokenBuilder2.Role.ROLE_PUBLISHER
                : RtcTokenBuilder2.Role.ROLE_SUBSCRIBER;

        String newToken = agoraTokenService.generateRtcToken(
                session.getAgoraChannelName(),
                participant.getAgoraUid(),
                agoraRole,
                TOKEN_EXPIRATION_SECONDS
        );

        participant.setAgoraToken(newToken);
        participant.setAgoraTokenExpiresAt(LocalDateTime.now().plusSeconds(TOKEN_EXPIRATION_SECONDS));
        participantRepository.save(participant);

        log.info("Token refreshed for user {} in session {}", userId, sessionId);

        return newToken;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean canUserJoinSession(String sessionId, Long userId) {
        LiveSession session = sessionRepository.findById(sessionId).orElse(null);
        if (session == null || session.getStatus() != SessionStatus.ACTIVE) {
            return false;
        }

        boolean isInvited = participantRepository.existsBySessionIdAndUserId(sessionId, userId);
        if (isInvited) {
            return true;
        }

        boolean isInProject = projectMemberRepository.existsByProjectIdAndUserId(
                session.getProject().getId(),
                userId
        );

        return isInProject;
    }

    @Override
    @Transactional(readOnly = true)
    public ParticipantRole determineParticipantRole(Long projectId, Long userId) {
        ProjectMember member = projectMemberRepository
                .findByProjectIdAndUserId(projectId, userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_IN_PROJECT));

        return mapProjectRoleToParticipantRole(member.getProjectRole());
    }

    @Override
    @Transactional
    public void removeParticipant(String sessionId, Long userId, Long removedBy) {
        log.info("Removing user {} from session {} by user {}", userId, sessionId, removedBy);

        LiveSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new AppException(ErrorCode.SESSION_NOT_FOUND));

        validateIsHost(session, removedBy);

        if (session.isHost(userId)) {
            throw new AppException(ErrorCode.CANNOT_REMOVE_HOST);
        }

        SessionParticipant participant = participantRepository
                .findBySessionIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new AppException(ErrorCode.PARTICIPANT_NOT_FOUND));

        if (participant.getIsOnline()) {
            participant.markAsOffline();
            participantRepository.save(participant);

            session.decrementParticipants();
            sessionRepository.save(session);
        }

        participantRepository.delete(participant);

        // ✅ Broadcast participant removed
        webSocketService.broadcastParticipantEvent(sessionId,
                ParticipantEvent.builder()
                        .action("REMOVED")
                        .userId(userId)
                        .userName(getFullName(participant.getUser()))
                        .role(participant.getParticipantRole())
                        .isOnline(false)
                        .currentParticipants(session.getCurrentParticipants())
                        .build()
        );

        // ✅ Send private notification to removed user
        webSocketService.sendToUser(userId, "/queue/notification",
                SystemNotification.builder()
                        .type("ERROR")
                        .title("Removed from Session")
                        .message("You have been removed from the session by the host")
                        .requiresAction(false)
                        .build()
        );

        // ✅ Send system notification to all
        webSocketService.broadcastSystemNotification(sessionId,
                SystemNotification.builder()
                        .type("WARNING")
                        .title("Participant Removed")
                        .message(getFullName(participant.getUser()) + " has been removed from the session")
                        .requiresAction(false)
                        .build()
        );

        log.info("User {} removed from session {}", userId, sessionId);
    }

    @Override
    @Transactional
    public void handleParticipantDisconnect(String sessionId, Long userId) {
        log.info("🔌 Handling WebSocket disconnect for user {} in session {}", userId, sessionId);

        try {
            SessionParticipant participant = participantRepository
                    .findBySessionIdAndUserId(sessionId, userId)
                    .orElse(null);

            if (participant == null) {
                log.debug("⚠️ No participant record found for userId {} in session {}", userId, sessionId);
                return;
            }

            if (!participant.getIsOnline()) {
                log.debug("ℹ️ Participant {} already offline in session {}", userId, sessionId);
                return;
            }

            // Mark participant as offline
            participant.markAsOffline();
            participantRepository.save(participant);

            // Update session participant count
            LiveSession session = sessionRepository.findById(sessionId).orElse(null);
            if (session != null) {
                session.decrementParticipants();
                sessionRepository.save(session);

                // Broadcast participant left event
                webSocketService.broadcastParticipantEvent(sessionId,
                        ParticipantEvent.builder()
                                .action("LEFT")
                                .userId(userId)
                                .userName(getFullName(participant.getUser()))
                                .userAvatarUrl(participant.getUser().getAvatarUrl())
                                .role(participant.getParticipantRole())
                                .isOnline(false)
                                .currentParticipants(session.getCurrentParticipants())
                                .build()
                );

                // Broadcast system notification
                webSocketService.broadcastSystemNotification(sessionId,
                        SystemNotification.builder()
                                .type("INFO")
                                .title("Participant Disconnected")
                                .message(getFullName(participant.getUser()) + " disconnected from the session")
                                .requiresAction(false)
                                .build()
                );
            }

            log.info("✅ Successfully handled disconnect for user {} from session {}", userId, sessionId);

        } catch (Exception e) {
            log.error("❌ Error handling disconnect for user {} in session {}: {}",
                    userId, sessionId, e.getMessage(), e);
        }
    }

    // ========== Private Helper Methods ==========

    private LiveSession validateSession(String sessionId) {
        return sessionRepository.findById(sessionId)
                .orElseThrow(() -> new AppException(ErrorCode.SESSION_NOT_FOUND));
    }

    private void validateIsHost(LiveSession session, Long userId) {
        if (!session.isHost(userId)) {
            throw new AppException(ErrorCode.ONLY_HOST_CAN_PERFORM_ACTION);
        }
    }

    private SessionParticipant createParticipantForUser(LiveSession session, Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        ParticipantRole role = determineParticipantRole(session.getProject().getId(), userId);

        return SessionParticipant.builder()
                .session(session)
                .user(user)
                .participantRole(role)
                .invitationStatus(InvitationStatus.PENDING)
                .canShareAudio(true)
                .canShareVideo(true)
                .canControlPlayback(role == ParticipantRole.OWNER)
                .audioEnabled(true)
                .videoEnabled(true)
                .isOnline(false)
                .build();
    }

    private int generateAgoraUid(Long userId) {
        return userId.intValue();
    }

    private ParticipantRole mapProjectRoleToParticipantRole(ProjectRole projectRole) {
        return switch (projectRole) {
            case OWNER -> ParticipantRole.OWNER;
            case COLLABORATOR -> ParticipantRole.COLLABORATOR;
            case CLIENT -> ParticipantRole.CLIENT;
            case OBSERVER -> ParticipantRole.OBSERVER;
        };
    }

    // ✅ Helper method to get full name
    private String getFullName(User user) {
        return user.getFirstName() + " " + user.getLastName();
    }
}