package com.fpt.producerworkbench.service.impl;

import com.fpt.producerworkbench.common.SessionStatus;
import com.fpt.producerworkbench.configuration.AgoraConfig;
import com.fpt.producerworkbench.dto.request.CreateSessionRequest;
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

@Service
@RequiredArgsConstructor
@Slf4j
public class LiveSessionServiceImpl implements LiveSessionService {

    private final LiveSessionRepository sessionRepository;
    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final SessionParticipantRepository participantRepository;
    private final LiveSessionMapper sessionMapper;
    private final WebSocketService webSocketService; // ✅ Add WebSocket service
    private final AgoraConfig agoraConfig;
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


        if (sessionRepository.hasActiveSessionForProject(request.getProjectId())) {
            throw new AppException(ErrorCode.PROJECT_ALREADY_HAS_ACTIVE_SESSION);
        }


        String channelName = generateChannelName(request.getProjectId());


        LiveSession session = LiveSession.builder()
                .project(project)
                .host(host)
                .title(request.getTitle())
                .description(request.getDescription())
                .sessionType(request.getSessionType())
                .status(SessionStatus.SCHEDULED)
                .agoraChannelName(channelName)
                .agoraAppId(getAgoraAppId())
                .maxParticipants(request.getMaxParticipants())
                .currentParticipants(0)
                .scheduledStart(request.getScheduledStart())
                .recordingEnabled(request.getRecordingEnabled())
                .build();

        LiveSession saved = sessionRepository.save(session);

        log.info("Session created: {}", saved.getId());

        return sessionMapper.toDTO(saved);
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
    public LiveSessionResponse pauseSession(String sessionId, Long userId) {
        log.info("Pausing session {} by user {}", sessionId, userId);

        LiveSession session = getSessionEntity(sessionId);
        validateIsHost(session, userId);

        if (session.getStatus() != SessionStatus.ACTIVE) {
            throw new AppException(ErrorCode.SESSION_NOT_ACTIVE);
        }

        User host = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        SessionStatus oldStatus = session.getStatus();
        session.setStatus(SessionStatus.PAUSED);
        LiveSession updated = sessionRepository.save(session);

        // ✅ Broadcast session paused
        webSocketService.broadcastSessionStateChange(sessionId,
                SessionStateChangeEvent.builder()
                        .sessionId(sessionId)
                        .oldStatus(oldStatus)
                        .newStatus(SessionStatus.PAUSED)
                        .triggeredBy(getFullName(host))
                        .triggeredByUserId(userId)
                        .message("Session paused")
                        .build()
        );

        // ✅ Send system notification
        webSocketService.broadcastSystemNotification(sessionId,
                SystemNotification.builder()
                        .type("WARNING")
                        .title("Session Paused")
                        .message("The session has been paused by " + getFullName(host))
                        .requiresAction(false)
                        .build()
        );

        log.info("Session {} paused", sessionId);

        return sessionMapper.toDTO(updated);
    }

    @Override
    @Transactional
    public LiveSessionResponse resumeSession(String sessionId, Long userId) {
        log.info("Resuming session {} by user {}", sessionId, userId);

        LiveSession session = getSessionEntity(sessionId);
        validateIsHost(session, userId);

        if (session.getStatus() != SessionStatus.PAUSED) {
            throw new AppException(ErrorCode.SESSION_NOT_PAUSED);
        }

        User host = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        SessionStatus oldStatus = session.getStatus();
        session.setStatus(SessionStatus.ACTIVE);
        LiveSession updated = sessionRepository.save(session);

        // ✅ Broadcast session resumed
        webSocketService.broadcastSessionStateChange(sessionId,
                SessionStateChangeEvent.builder()
                        .sessionId(sessionId)
                        .oldStatus(oldStatus)
                        .newStatus(SessionStatus.ACTIVE)
                        .triggeredBy(getFullName(host))
                        .triggeredByUserId(userId)
                        .message("Session resumed")
                        .build()
        );

        // ✅ Send system notification
        webSocketService.broadcastSystemNotification(sessionId,
                SystemNotification.builder()
                        .type("INFO")
                        .title("Session Resumed")
                        .message("The session has been resumed")
                        .requiresAction(false)
                        .build()
        );

        log.info("Session {} resumed", sessionId);

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
        return sessionMapper.toDTO(session);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<LiveSessionResponse> getSessionsByProject(Long projectId, SessionStatus status, Pageable pageable,Long currentUserId) {
        Page<LiveSession> sessions;

        if (status != null) {
            sessions = sessionRepository.findByProjectIdAndStatus(projectId, status, pageable);
        } else {
            sessions = sessionRepository.findByProjectId(projectId, pageable);
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
                .recordingUrl(session.getRecordingUrl())
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
}
