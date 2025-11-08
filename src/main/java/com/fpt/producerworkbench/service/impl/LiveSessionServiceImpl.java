package com.fpt.producerworkbench.service.impl;

import com.fpt.producerworkbench.common.SessionStatus;
import com.fpt.producerworkbench.configuration.AgoraConfig;
import com.fpt.producerworkbench.dto.request.CreateSessionRequest;
import com.fpt.producerworkbench.dto.request.UpdateSessionRequest;
import com.fpt.producerworkbench.dto.response.LiveSessionResponse;
import com.fpt.producerworkbench.dto.response.SessionSummaryResponse;
import com.fpt.producerworkbench.dto.websocket.SessionStateChangeEvent;
import com.fpt.producerworkbench.dto.websocket.SystemNotification;
import com.fpt.producerworkbench.entity.LiveSession;
import com.fpt.producerworkbench.entity.Project;
import com.fpt.producerworkbench.entity.User;
import com.fpt.producerworkbench.exception.AppException;
import com.fpt.producerworkbench.exception.ErrorCode;
import com.fpt.producerworkbench.mapper.LiveSessionMapper;
import com.fpt.producerworkbench.repository.LiveSessionRepository;
import com.fpt.producerworkbench.repository.ProjectRepository;
import com.fpt.producerworkbench.repository.SessionParticipantRepository;
import com.fpt.producerworkbench.repository.UserRepository;
import com.fpt.producerworkbench.service.EmailService;
import com.fpt.producerworkbench.service.LiveSessionService;
import com.fpt.producerworkbench.service.WebSocketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class LiveSessionServiceImpl implements LiveSessionService {

    private final LiveSessionRepository sessionRepository;
    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final SessionParticipantRepository participantRepository;
    private final com.fpt.producerworkbench.repository.ProjectMemberRepository projectMemberRepository;
    private final LiveSessionMapper sessionMapper;
    private final WebSocketService webSocketService; // ✅ Add WebSocket service
    private final AgoraConfig agoraConfig;
    private final com.fpt.producerworkbench.utils.SecurityUtils securityUtils; // ✅ Add SecurityUtils
    private final EmailService emailService; // ✅ Add Email service
    
    @Override
    @Transactional
    public LiveSessionResponse createSession(CreateSessionRequest request, Long hostId) {
        log.info("Creating session for project {} by user {}", request.getProjectId(), hostId);

        Project project = projectRepository.findById(request.getProjectId())
                .orElseThrow(() -> new AppException(ErrorCode.PROJECT_NOT_FOUND));


        User host = userRepository.findById(hostId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));


        if (!project.getCreator().getId().equals(hostId)) {
            throw new AppException(ErrorCode.ONLY_PROJECT_OWNER_CAN_CREATE_SESSION);
        }

        // ✅ NEW: Validate scheduledStart if provided
        if (request.getScheduledStart() != null 
            && request.getScheduledStart().isBefore(LocalDateTime.now())) {
            throw new AppException(ErrorCode.SCHEDULED_START_MUST_BE_FUTURE);
        }

        // ✅ NEW: Check max concurrent sessions (max 3 SCHEDULED/ACTIVE sessions)
        long activeSessions = sessionRepository.countActiveSessionsForProject(request.getProjectId());
        if (activeSessions >= 3) {
            throw new AppException(ErrorCode.MAX_CONCURRENT_SESSIONS_REACHED);
        }

        String channelName = generateChannelName(request.getProjectId());

        // ✅ Determine if session is public or private based on invites
        boolean hasInvites = (request.getInvitedMemberIds() != null && !request.getInvitedMemberIds().isEmpty())
                || (request.getInviteRoles() != null && !request.getInviteRoles().isEmpty());
        boolean isPublic = !hasInvites; // If no invites -> PUBLIC, otherwise PRIVATE

        LiveSession session = LiveSession.builder()
                .project(project)
                .host(host)
                .title(request.getTitle())
                .description(request.getDescription())
                .status(SessionStatus.SCHEDULED)
                .agoraChannelName(channelName)
                .agoraAppId(getAgoraAppId())
                .currentParticipants(0)
                .scheduledStart(request.getScheduledStart())
                .isPublic(isPublic)
                .build();

        LiveSession saved = sessionRepository.save(session);

        log.info("Session created: {}", saved.getId());

        // ✅ Send email notification and create SessionParticipant for invited members
        try {
            List<com.fpt.producerworkbench.entity.ProjectMember> invitedMembers = 
                determineInvitedMembers(request, hostId);

            if (!invitedMembers.isEmpty()) {
                // Create SessionParticipant for each invited member
                for (com.fpt.producerworkbench.entity.ProjectMember member : invitedMembers) {
                    com.fpt.producerworkbench.common.ParticipantRole role = 
                        mapProjectRoleToParticipantRole(member.getProjectRole());
                    
                    com.fpt.producerworkbench.entity.SessionParticipant participant = 
                        com.fpt.producerworkbench.entity.SessionParticipant.builder()
                            .session(saved)
                            .user(member.getUser())
                            .participantRole(role)
                            .invitationStatus(com.fpt.producerworkbench.common.InvitationStatus.PENDING)
                            .invitedAt(LocalDateTime.now())
                            .canShareAudio(true)
                            .canShareVideo(true)
                            .canControlPlayback(role == com.fpt.producerworkbench.common.ParticipantRole.OWNER)
                            .canApproveFiles(role == com.fpt.producerworkbench.common.ParticipantRole.OWNER)
                            .audioEnabled(true)
                            .videoEnabled(true)
                            .isOnline(false)
                            .build();
                    
                    participantRepository.save(participant);
                }
                
                // Send email to all invited members
                List<String> memberEmails = invitedMembers.stream()
                        .map(member -> member.getUser().getEmail())
                        .collect(Collectors.toList());
                
                emailService.sendSessionInviteNotification(saved, memberEmails);
                log.info("Session invite emails sent to {} members and SessionParticipants created", 
                        memberEmails.size());
            } else {
                log.info("No members invited for session: {}", saved.getId());
            }
        } catch (Exception e) {
            log.error("Failed to send session invite emails: {}", e.getMessage(), e);
            // Don't fail the session creation if email fails
        }

        return sessionMapper.toDTO(saved);
    }

    @Override
    @Transactional
    public LiveSessionResponse updateSession(String sessionId, UpdateSessionRequest request, Long userId) {
        log.info("Updating session {} by user {}", sessionId, userId);

        LiveSession session = getSessionEntity(sessionId);
        validateIsHost(session, userId);

        // ❌ Cannot update ACTIVE sessions
        if (session.getStatus() == SessionStatus.ACTIVE) {
            throw new AppException(ErrorCode.CANNOT_UPDATE_ACTIVE_SESSION);
        }

        // ✅ Only update SCHEDULED or ENDED sessions
        if (session.getStatus() != SessionStatus.SCHEDULED && session.getStatus() != SessionStatus.ENDED) {
            throw new AppException(ErrorCode.CAN_ONLY_UPDATE_SCHEDULED_OR_ENDED_SESSION);
        }

        // Update title if provided
        if (request.getTitle() != null) {
            session.setTitle(request.getTitle());
        }

        // Update description if provided
        if (request.getDescription() != null) {
            session.setDescription(request.getDescription());
        }

        // Update scheduledStart if provided and validate it's in the future
        if (request.getScheduledStart() != null) {
            if (request.getScheduledStart().isBefore(LocalDateTime.now())) {
                throw new AppException(ErrorCode.SCHEDULED_START_MUST_BE_FUTURE);
            }
            session.setScheduledStart(request.getScheduledStart());
        }

        LiveSession updated = sessionRepository.save(session);
        log.info("Session {} updated successfully", sessionId);

        return sessionMapper.toDTO(updated);
    }

    @Override
    @Transactional
    public void deleteSession(String sessionId, Long userId) {
        log.info("Deleting session {} by user {}", sessionId, userId);

        LiveSession session = getSessionEntity(sessionId);
        validateIsHost(session, userId);

        // ❌ Cannot delete ACTIVE sessions - must END first
        if (session.getStatus() == SessionStatus.ACTIVE) {
            throw new AppException(ErrorCode.CANNOT_DELETE_ACTIVE_SESSION);
        }

        // ✅ Only delete SCHEDULED or ENDED sessions
        if (session.getStatus() != SessionStatus.SCHEDULED && session.getStatus() != SessionStatus.ENDED) {
            throw new AppException(ErrorCode.CAN_ONLY_DELETE_SCHEDULED_OR_ENDED_SESSION);
        }

        // Cascade delete all participants
        participantRepository.deleteBySessionId(sessionId);

        // Delete session
        sessionRepository.delete(session);

        log.info("Session {} deleted successfully", sessionId);
    }

    @Override
    @Transactional
    public LiveSessionResponse startSession(String sessionId, Long userId) {
        log.info("Starting session {} by user {}", sessionId, userId);


        LiveSession session = getSessionEntity(sessionId);


        validateIsHost(session, userId);


        if (session.getStatus() != SessionStatus.SCHEDULED) {
            throw new AppException(ErrorCode.SESSION_ALREADY_STARTED);
        }


        User host = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));


        SessionStatus oldStatus = session.getStatus();
        session.setStatus(SessionStatus.ACTIVE);
        session.setActualStart(LocalDateTime.now());

        LiveSession updated = sessionRepository.save(session);

        // ✅ Broadcast session started via WebSocket
        webSocketService.broadcastSessionStateChange(sessionId,
                SessionStateChangeEvent.builder()
                        .sessionId(sessionId)
                        .oldStatus(oldStatus)
                        .newStatus(SessionStatus.ACTIVE)
                        .triggeredBy(getFullName(host))
                        .triggeredByUserId(userId)
                        .message("Session has started")
                        .build()
        );

        // ✅ Send system notification
        webSocketService.broadcastSystemNotification(sessionId,
                SystemNotification.builder()
                        .type("SUCCESS")
                        .title("Session Started")
                        .message(session.getTitle() + " is now live!")
                        .requiresAction(false)
                        .build()
        );

        log.info("Session {} started", sessionId);

        return sessionMapper.toDTO(updated);
    }

    @Override
    @Transactional
    public SessionSummaryResponse endSession(String sessionId, Long userId) {
        log.info("Ending session {} by user {}", sessionId, userId);

        LiveSession session = getSessionEntity(sessionId);
        validateIsHost(session, userId);

        if (session.getStatus() == SessionStatus.ENDED || session.getStatus() == SessionStatus.CANCELLED) {
            throw new AppException(ErrorCode.SESSION_ALREADY_ENDED);
        }

        User host = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        SessionStatus oldStatus = session.getStatus();

        // Update session
        session.setStatus(SessionStatus.ENDED);
        session.setActualEnd(LocalDateTime.now());
        session.setCurrentParticipants(0);

        sessionRepository.save(session);

        // Mark all participants as offline
        participantRepository.findBySessionId(sessionId).forEach(p -> {
            if (p.getIsOnline()) {
                p.markAsOffline();
                participantRepository.save(p);
            }
        });

        // Build summary
        SessionSummaryResponse summary = buildSessionSummary(session);

        // ✅ Broadcast session ended
        webSocketService.broadcastSessionStateChange(sessionId,
                SessionStateChangeEvent.builder()
                        .sessionId(sessionId)
                        .oldStatus(oldStatus)
                        .newStatus(SessionStatus.ENDED)
                        .triggeredBy(getFullName(host))
                        .triggeredByUserId(userId)
                        .message("Session has ended")
                        .build()
        );

        // ✅ Broadcast session summary
        webSocketService.broadcastSessionSummary(sessionId, summary);

        // ✅ Send system notification
        webSocketService.broadcastSystemNotification(sessionId,
                SystemNotification.builder()
                        .type("INFO")
                        .title("Session Ended")
                        .message("Thank you for participating! Duration: " + summary.getDuration())
                        .requiresAction(false)
                        .build()
        );

        log.info("Session {} ended", sessionId);

        return summary;
    }

    @Override
    @Transactional
    public LiveSessionResponse cancelSession(String sessionId, Long userId, String reason) {
        log.info("Cancelling session {} by user {}", sessionId, userId);

        LiveSession session = getSessionEntity(sessionId);
        validateIsHost(session, userId);

        if (session.getStatus() != SessionStatus.SCHEDULED) {
            throw new AppException(ErrorCode.CAN_ONLY_CANCEL_SCHEDULED_SESSION);
        }

        User host = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        SessionStatus oldStatus = session.getStatus();
        session.setStatus(SessionStatus.CANCELLED);
        LiveSession updated = sessionRepository.save(session);

        // ✅ Broadcast session cancelled
        webSocketService.broadcastSessionStateChange(sessionId,
                SessionStateChangeEvent.builder()
                        .sessionId(sessionId)
                        .oldStatus(oldStatus)
                        .newStatus(SessionStatus.CANCELLED)
                        .triggeredBy(getFullName(host))
                        .triggeredByUserId(userId)
                        .message(reason != null ? reason : "Session cancelled")
                        .build()
        );

        // ✅ Send system notification
        webSocketService.broadcastSystemNotification(sessionId,
                SystemNotification.builder()
                        .type("WARNING")
                        .title("Session Cancelled")
                        .message(reason != null ? reason : "This session has been cancelled")
                        .requiresAction(false)
                        .build()
        );

        log.info("Session {} cancelled. Reason: {}", sessionId, reason);

        return sessionMapper.toDTO(updated);
    }

    @Override
    @Transactional(readOnly = true)
    public LiveSessionResponse getSessionById(String sessionId) {
        LiveSession session = getSessionEntity(sessionId);
        LiveSessionResponse response = sessionMapper.toDTO(session);
        
        // ✅ Get current user ID from SecurityContext
        try {
            Long currentUserId = securityUtils.getCurrentUserId();
            response.setCurrentUserId(currentUserId);
        } catch (Exception e) {
            log.warn("⚠️ Could not get current user ID for session {}: {}", sessionId, e.getMessage());
        }
        
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<LiveSessionResponse> getSessionsByProject(Long projectId, SessionStatus status, Pageable pageable, Long currentUserId) {
        // ✅ NEW: Check if user is project member
        com.fpt.producerworkbench.entity.ProjectMember member = projectMemberRepository
                .findByProjectIdAndUserId(projectId, currentUserId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_PROJECT_MEMBER));

        // ❌ NEW: Block anonymous members from viewing sessions
        if (member.isAnonymous()) {
            throw new AppException(ErrorCode.ANONYMOUS_MEMBER_CANNOT_ACCESS_SESSION);
        }

        // ✅ NEW: Use smart query to filter sessions based on visibility
        // User sees: sessions they host + sessions they're invited to + public sessions
        Page<LiveSession> sessions;

        if (status != null) {
            sessions = sessionRepository.findSessionsVisibleToUserByStatus(projectId, currentUserId, status, pageable);
        } else {
            sessions = sessionRepository.findSessionsVisibleToUser(projectId, currentUserId, pageable);
        }

        return sessions.map(session -> {
            LiveSessionResponse dto = sessionMapper.toDTO(session);
            dto.setCurrentUserId(currentUserId);  // ✅ SET CURRENT USER ID
            return dto;
        });
    }

    @Override
    @Transactional(readOnly = true)
    public Page<LiveSessionResponse> getSessionsByHost(Long hostId, Pageable pageable) {
        Page<LiveSession> sessions = sessionRepository.findByHostId(hostId, pageable);
        return sessions.map(sessionMapper::toDTO);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasActiveSession(Long projectId) {
        return sessionRepository.hasActiveSessionForProject(projectId);
    }

    // ========== Private Helper Methods ==========

    private LiveSession getSessionEntity(String sessionId) {
        return sessionRepository.findById(sessionId)
                .orElseThrow(() -> new AppException(ErrorCode.SESSION_NOT_FOUND));
    }

    private void validateIsHost(LiveSession session, Long userId) {
        if (!session.isHost(userId)) {
            throw new AppException(ErrorCode.ONLY_HOST_CAN_PERFORM_ACTION);
        }
    }

    private String generateChannelName(Long projectId) {
        return "channel-" + projectId + "-" + System.currentTimeMillis();
    }

    private String getAgoraAppId() {
        // Get from AgoraConfig or environment
        return agoraConfig.getAppId(); // TODO: Inject from config
    }

    private SessionSummaryResponse buildSessionSummary(LiveSession session) {
        String duration = calculateDuration(session.getActualStart(), session.getActualEnd());
        Long totalParticipants = participantRepository.countBySessionId(session.getId());

        return SessionSummaryResponse.builder()
                .sessionId(session.getId())
                .title(session.getTitle())
                .scheduledStart(session.getScheduledStart())
                .actualStart(session.getActualStart())
                .actualEnd(session.getActualEnd())
                .duration(duration)
                .totalParticipants(totalParticipants.intValue())
                .build();
    }

    private String calculateDuration(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null) return "N/A";

        Duration duration = Duration.between(start, end);
        long hours = duration.toHours();
        long minutes = duration.toMinutes() % 60;

        if (hours > 0) {
            return String.format("%dh %dm", hours, minutes);
        } else {
            return String.format("%dm", minutes);
        }
    }

    // ✅ Helper method to get full name
    private String getFullName(User user) {
        return user.getFirstName() + " " + user.getLastName();
    }

    // ✅ Determine which members to invite based on request
    private List<com.fpt.producerworkbench.entity.ProjectMember> determineInvitedMembers(
            CreateSessionRequest request, Long hostId) {
        
        List<com.fpt.producerworkbench.entity.ProjectMember> allMembers = 
            projectMemberRepository.findByProjectId(request.getProjectId());
        
        // Exclude host from invitation
        allMembers = allMembers.stream()
                .filter(member -> !member.getUser().getId().equals(hostId))
                .collect(Collectors.toList());
        
        // If specific member IDs provided, filter by them
        if (request.getInvitedMemberIds() != null && !request.getInvitedMemberIds().isEmpty()) {
            return allMembers.stream()
                    .filter(member -> request.getInvitedMemberIds().contains(member.getUser().getId()))
                    .collect(Collectors.toList());
        }
        
        // If roles provided, filter by roles
        if (request.getInviteRoles() != null && !request.getInviteRoles().isEmpty()) {
            return allMembers.stream()
                    .filter(member -> request.getInviteRoles().contains(member.getProjectRole()))
                    .collect(Collectors.toList());
        }
        
        // If neither provided, return empty list (no invitations)
        return List.of();
    }

    // ✅ Map ProjectRole to ParticipantRole
    private com.fpt.producerworkbench.common.ParticipantRole mapProjectRoleToParticipantRole(
            com.fpt.producerworkbench.common.ProjectRole projectRole) {
        return switch (projectRole) {
            case OWNER -> com.fpt.producerworkbench.common.ParticipantRole.OWNER;
            case COLLABORATOR -> com.fpt.producerworkbench.common.ParticipantRole.COLLABORATOR;
            case CLIENT -> com.fpt.producerworkbench.common.ParticipantRole.OBSERVER;
            default -> com.fpt.producerworkbench.common.ParticipantRole.OBSERVER;
        };
    }
}
