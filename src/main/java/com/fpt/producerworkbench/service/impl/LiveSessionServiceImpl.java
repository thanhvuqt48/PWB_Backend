package com.fpt.producerworkbench.service.impl;

import com.fpt.producerworkbench.common.SessionStatus;
import com.fpt.producerworkbench.configuration.AgoraConfig;
import com.fpt.producerworkbench.dto.request.CreateSessionRequest;
import com.fpt.producerworkbench.dto.request.InviteMoreMembersRequest;
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

        // ✅ ONLY allow update for SCHEDULED sessions
        if (session.getStatus() != SessionStatus.SCHEDULED) {
            throw new AppException(ErrorCode.CAN_ONLY_UPDATE_SCHEDULED_SESSION);
        }

        // ✅ Track if scheduledStart is changed for email notification
        boolean scheduledStartChanged = false;
        LocalDateTime oldScheduledStart = session.getScheduledStart();

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
            // ✅ Check if scheduledStart actually changed
            if (oldScheduledStart == null || !oldScheduledStart.equals(request.getScheduledStart())) {
                scheduledStartChanged = true;
            }
            session.setScheduledStart(request.getScheduledStart());
        }

        LiveSession updated = sessionRepository.save(session);
        log.info("Session {} updated successfully", sessionId);

        // ✅ Send email notification if scheduledStart changed
        if (scheduledStartChanged && !Boolean.TRUE.equals(session.getIsPublic())) {
            try {
                List<String> invitedMemberEmails = participantRepository.findBySessionId(updated.getId())
                        .stream()
                        .filter(p -> !p.getUser().getId().equals(userId)) // Exclude host
                        .map(p -> p.getUser().getEmail())
                        .collect(Collectors.toList());

                if (!invitedMemberEmails.isEmpty()) {
                    emailService.sendSessionScheduleChangeNotification(
                            updated, invitedMemberEmails, oldScheduledStart);
                    log.info("Sent schedule change notification to {} members", 
                            invitedMemberEmails.size());
                }
            } catch (Exception e) {
                log.error("Failed to send schedule change emails: {}", e.getMessage(), e);
            }
        }

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

        // ✅ Only delete SCHEDULED, ENDED, or CANCELLED sessions
        if (session.getStatus() != SessionStatus.SCHEDULED 
            && session.getStatus() != SessionStatus.ENDED
            && session.getStatus() != SessionStatus.CANCELLED) {
            throw new AppException(ErrorCode.CAN_ONLY_DELETE_SCHEDULED_ENDED_OR_CANCELLED_SESSION);
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
        session.updateActivity(); // ✅ Update activity time

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

        // ✅ Send cancellation email to invited participants
        try {
            List<String> participantEmails = participantRepository.findBySessionId(sessionId)
                    .stream()
                    .filter(p -> !p.getUser().getId().equals(userId)) // Exclude host
                    .map(p -> p.getUser().getEmail())
                    .collect(Collectors.toList());

            if (!participantEmails.isEmpty()) {
                emailService.sendSessionCancellationEmail(updated, participantEmails, reason);
                log.info("Cancellation emails sent to {} participants", participantEmails.size());
            } else {
                log.info("No participants to notify about cancellation");
            }
        } catch (Exception e) {
            log.error("Failed to send cancellation emails: {}", e.getMessage(), e);
            // Don't fail the cancellation if email fails
        }

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
        return agoraConfig.getId(); // TODO: Inject from config
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

    // ✅ NEW: Invite more members to SCHEDULED PRIVATE session
    @Override
    @Transactional
    public LiveSessionResponse inviteMoreMembers(String sessionId, 
            com.fpt.producerworkbench.dto.request.InviteMoreMembersRequest request, Long userId) {
        log.info("Inviting more members to session {} by user {}", sessionId, userId);

        LiveSession session = getSessionEntity(sessionId);
        validateIsHost(session, userId);

        // ✅ Only allow inviting to SCHEDULED sessions
        if (session.getStatus() != SessionStatus.SCHEDULED) {
            throw new AppException(ErrorCode.CAN_ONLY_INVITE_TO_SCHEDULED_SESSION);
        }

        // ✅ Only allow inviting to PRIVATE sessions
        if (Boolean.TRUE.equals(session.getIsPublic())) {
            throw new AppException(ErrorCode.CAN_ONLY_INVITE_TO_PRIVATE_SESSION);
        }

        // ✅ Validate memberIds and roles have same size
        if (request.getMemberIds().size() != request.getRoles().size()) {
            throw new AppException(ErrorCode.MEMBER_IDS_AND_ROLES_MUST_MATCH);
        }

        // ✅ Get existing invited member IDs to prevent duplicates
        List<Long> existingInvitedMemberIds = participantRepository
                .findBySessionId(sessionId)
                .stream()
                .map(p -> p.getUser().getId())
                .collect(Collectors.toList());

        List<String> newMemberEmails = new java.util.ArrayList<>();
        int invitedCount = 0;

        for (int i = 0; i < request.getMemberIds().size(); i++) {
            Long memberId = request.getMemberIds().get(i);
            com.fpt.producerworkbench.common.ProjectRole role = request.getRoles().get(i);

            // ✅ Skip if already invited
            if (existingInvitedMemberIds.contains(memberId)) {
                log.warn("Member {} already invited to session {}", memberId, sessionId);
                continue;
            }

            // ✅ Validate member is in the project
            com.fpt.producerworkbench.entity.ProjectMember projectMember = 
                    projectMemberRepository.findByProjectIdAndUserId(session.getProject().getId(), memberId)
                    .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_IN_PROJECT));

            // ✅ Map role to participant role
            com.fpt.producerworkbench.common.ParticipantRole participantRole = 
                    mapProjectRoleToParticipantRole(role);

            // ✅ Create SessionParticipant
            com.fpt.producerworkbench.entity.SessionParticipant participant = 
                    com.fpt.producerworkbench.entity.SessionParticipant.builder()
                    .session(session)
                    .user(projectMember.getUser())
                    .participantRole(participantRole)
                    .invitationStatus(com.fpt.producerworkbench.common.InvitationStatus.PENDING)
                    .invitedAt(LocalDateTime.now())
                    .canShareAudio(true)
                    .canShareVideo(true)
                    .canControlPlayback(participantRole == com.fpt.producerworkbench.common.ParticipantRole.OWNER)
                    .canApproveFiles(participantRole == com.fpt.producerworkbench.common.ParticipantRole.OWNER)
                    .audioEnabled(true)
                    .videoEnabled(true)
                    .isOnline(false)
                    .build();

            participantRepository.save(participant);
            newMemberEmails.add(projectMember.getUser().getEmail());
            invitedCount++;
        }

        // ✅ Send email to newly invited members
        if (!newMemberEmails.isEmpty()) {
            try {
                // ✅ IMPORTANT: Fetch all lazy-loaded fields BEFORE async call to avoid Hibernate LazyInitializationException
                session.getProject().getTitle(); // Force load project title
                session.getTitle(); // Force load session title
                
                emailService.sendSessionInviteNotification(session, newMemberEmails);
                log.info("Sent invitation emails to {} new members for session {}", 
                        newMemberEmails.size(), sessionId);
            } catch (Exception e) {
                log.error("Failed to send invitation emails: {}", e.getMessage(), e);
            }
        }

        log.info("Successfully invited {} new members to session {}", invitedCount, sessionId);
        return sessionMapper.toDTO(session);
    }

    // ✅ NEW: Get available members (exclude already invited)
    @Override
    @Transactional(readOnly = true)
    public List<com.fpt.producerworkbench.dto.response.AvailableMemberResponse> getAvailableMembers(
            String sessionId, Long userId) {
        log.info("Getting available members for session {} by user {}", sessionId, userId);

        LiveSession session = getSessionEntity(sessionId);
        validateIsHost(session, userId);

        // ✅ Get all project members
        List<com.fpt.producerworkbench.entity.ProjectMember> allProjectMembers = 
                projectMemberRepository.findByProjectId(session.getProject().getId());

        // ✅ Get already invited member IDs
        List<Long> invitedMemberIds = participantRepository.findBySessionId(sessionId)
                .stream()
                .map(p -> p.getUser().getId())
                .collect(Collectors.toList());

        // ✅ Filter out already invited members AND the host/owner (don't need to invite themselves)
        return allProjectMembers.stream()
                .filter(pm -> !invitedMemberIds.contains(pm.getUser().getId()))
                .filter(pm -> !pm.getUser().getId().equals(userId)) // ✅ Exclude the host
                .map(pm -> com.fpt.producerworkbench.dto.response.AvailableMemberResponse.builder()
                        .userId(pm.getUser().getId())
                        .username(pm.getUser().getUsername())
                        .email(pm.getUser().getEmail())
                        .fullName(pm.getUser().getFirstName() + " " + pm.getUser().getLastName())
                        .projectRole(pm.getProjectRole())
                        .build())
                .collect(Collectors.toList());
    }
}
