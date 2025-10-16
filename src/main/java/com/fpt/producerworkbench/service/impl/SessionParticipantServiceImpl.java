package com.fpt.producerworkbench.service.impl;

import com.fpt.producerworkbench.service.SessionParticipantService;
import com.fpt.producerworkbench.common.InvitationStatus;
import com.fpt.producerworkbench.common.ParticipantRole;
import com.fpt.producerworkbench.common.ProjectRole;
import com.fpt.producerworkbench.common.SessionStatus;
import com.fpt.producerworkbench.dto.request.InviteParticipantRequest;
import com.fpt.producerworkbench.dto.request.UpdateParticipantPermissionRequest;
import com.fpt.producerworkbench.dto.response.JoinSessionResponse;
import com.fpt.producerworkbench.dto.response.SessionParticipantResponse;
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
import io.agora.media.RtcTokenBuilder.Role;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

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

    private static final int TOKEN_EXPIRATION_SECONDS = 86400; // 24 hours

    @Override
    @Transactional
    public SessionParticipantResponse inviteParticipant(
            String sessionId,
            InviteParticipantRequest request,
            Long invitedBy) {

        log.info("Inviting user {} to session {} by user {}", request.getUserId(), sessionId, invitedBy);

        // Validate session
        LiveSession session = validateSession(sessionId);

        // Validate inviter is host
        validateIsHost(session, invitedBy);

        // Validate invitee exists
        User invitee = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        // Check if already invited
        if (participantRepository.existsBySessionIdAndUserId(sessionId, request.getUserId())) {
            throw new AppException(ErrorCode.USER_ALREADY_INVITED);
        }

        // Determine role based on project membership
        ParticipantRole role = determineParticipantRole(session.getProject().getId(), request.getUserId());

        // Create participant
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

        log.info("User {} invited to session {} with role {}", request.getUserId(), sessionId, role);

        // TODO: Send notification to invitee

        return participantMapper.toDTO(saved);
    }

    @Override
    @Transactional
    public JoinSessionResponse joinSession(String sessionId, Long userId) {
        log.info("User {} joining session {}", userId, sessionId);

        // Validate session
        LiveSession session = validateSession(sessionId);

        // Check session is active
        if (session.getStatus() != SessionStatus.ACTIVE) {
            throw new AppException(ErrorCode.SESSION_NOT_ACTIVE);
        }

        // Check session not full
        if (session.isFull()) {
            throw new AppException(ErrorCode.SESSION_FULL);
        }

        // Get or create participant
        SessionParticipant participant = participantRepository
                .findBySessionIdAndUserId(sessionId, userId)
                .orElseGet(() -> createParticipantForUser(session, userId));

        // Check invitation status
        if (participant.getInvitationStatus() == InvitationStatus.DECLINED) {
            throw new AppException(ErrorCode.INVITATION_DECLINED);
        }

        // Generate Agora UID
        int agoraUid = generateAgoraUid(userId);

        // Determine Agora role
        Role agoraRole = participant.canPublish() ? Role.Role_Publisher : Role.Role_Subscriber;

        // Generate Agora token
        String agoraToken = agoraTokenService.generateRtcToken(
                session.getAgoraChannelName(),
                agoraUid,
                agoraRole,
                TOKEN_EXPIRATION_SECONDS
        );

        // Update participant
        participant.setAgoraUid(agoraUid);
        participant.setAgoraToken(agoraToken);
        participant.setAgoraTokenExpiresAt(LocalDateTime.now().plusSeconds(TOKEN_EXPIRATION_SECONDS));
        participant.setInvitationStatus(InvitationStatus.ACCEPTED);
        participant.markAsOnline();

        participantRepository.save(participant);

        // Update session participant count
        session.incrementParticipants();
        sessionRepository.save(session);

        log.info("User {} joined session {} successfully with UID {}", userId, sessionId, agoraUid);

        // TODO: Broadcast via WebSocket: user joined

        return JoinSessionResponse.builder()
                .sessionId(sessionId)
                .channelName(session.getAgoraChannelName())
                .appId(session.getAgoraAppId())
                .token(agoraToken)
                .uid(agoraUid)
                .role(agoraRole.name())
                .expiresIn(TOKEN_EXPIRATION_SECONDS)
                .build();
    }

    @Override
    @Transactional
    public void leaveSession(String sessionId, Long userId) {
        log.info("User {} leaving session {}", userId, sessionId);

        // Get participant
        SessionParticipant participant = participantRepository
                .findBySessionIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new AppException(ErrorCode.PARTICIPANT_NOT_FOUND));

        // Mark as offline
        participant.markAsOffline();
        participantRepository.save(participant);

        // Update session participant count
        LiveSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new AppException(ErrorCode.SESSION_NOT_FOUND));

        session.decrementParticipants();
        sessionRepository.save(session);

        log.info("User {} left session {}", userId, sessionId);

        // TODO: Broadcast via WebSocket: user left
    }

    @Override
    @Transactional(readOnly = true)
    public List<SessionParticipantResponse> getSessionParticipants(String sessionId) {
        log.debug("Getting participants for session {}", sessionId);

        List<SessionParticipant> participants = participantRepository.findBySessionId(sessionId);
        return participants.stream()
                .map(participantMapper::toDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<SessionParticipantResponse> getOnlineParticipants(String sessionId) {
        log.debug("Getting online participants for session {}", sessionId);

        List<SessionParticipant> participants = participantRepository
                .findOnlineParticipantsBySessionId(sessionId);

        return participants.stream()
                .map(participantMapper::toDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public SessionParticipantResponse updateParticipantPermissions(
            String sessionId,
            Long userId,
            UpdateParticipantPermissionRequest request,
            Long updatedBy) {

        log.info("Updating permissions for user {} in session {} by {}", userId, sessionId, updatedBy);

        // Validate session
        LiveSession session = validateSession(sessionId);

        // Validate updater is host
        validateIsHost(session, updatedBy);

        // Get participant
        SessionParticipant participant = participantRepository
                .findBySessionIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new AppException(ErrorCode.PARTICIPANT_NOT_FOUND));

        // Update permissions
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

        log.info("Permissions updated for user {} in session {}", userId, sessionId);

        // TODO: Broadcast via WebSocket: permissions updated

        return participantMapper.toDTO(updated);
    }

    @Override
    @Transactional
    public String refreshAgoraToken(String sessionId, Long userId) {
        log.info("Refreshing Agora token for user {} in session {}", userId, sessionId);

        // Get participant
        SessionParticipant participant = participantRepository
                .findBySessionIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new AppException(ErrorCode.PARTICIPANT_NOT_FOUND));

        // Get session
        LiveSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new AppException(ErrorCode.SESSION_NOT_FOUND));

        // Generate new token
        Role agoraRole = participant.canPublish() ? Role.Role_Publisher : Role.Role_Subscriber;
        String newToken = agoraTokenService.generateRtcToken(
                session.getAgoraChannelName(),
                participant.getAgoraUid(),
                agoraRole,
                TOKEN_EXPIRATION_SECONDS
        );

        // Update participant
        participant.setAgoraToken(newToken);
        participant.setAgoraTokenExpiresAt(LocalDateTime.now().plusSeconds(TOKEN_EXPIRATION_SECONDS));
        participantRepository.save(participant);

        log.info("Token refreshed for user {} in session {}", userId, sessionId);

        return newToken;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean canUserJoinSession(String sessionId, Long userId) {
        // Check session exists and is active
        LiveSession session = sessionRepository.findById(sessionId).orElse(null);
        if (session == null || session.getStatus() != SessionStatus.ACTIVE) {
            return false;
        }

        // Check session not full
        if (session.isFull()) {
            return false;
        }

        // Check user is invited or is in project
        boolean isInvited = participantRepository.existsBySessionIdAndUserId(sessionId, userId);
        if (isInvited) {
            return true;
        }

        // Check user is in project
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
        // Generate unique UID from user ID
        return userId.intValue() % 1000000;
    }

    private ParticipantRole mapProjectRoleToParticipantRole(ProjectRole projectRole) {
        return switch (projectRole) {
            case OWNER -> ParticipantRole.OWNER;
            case COLLABORATOR -> ParticipantRole.COLLABORATOR;
            case CLIENT -> ParticipantRole.CLIENT;
            case OBSERVER -> ParticipantRole.OBSERVER;
        };
    }
}
