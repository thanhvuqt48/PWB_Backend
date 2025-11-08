package com.fpt.producerworkbench.service.impl;

import com.fpt.producerworkbench.common.ProjectRole;
import com.fpt.producerworkbench.common.SessionStatus;
import com.fpt.producerworkbench.dto.websocket.JoinRequest;
import com.fpt.producerworkbench.entity.LiveSession;
import com.fpt.producerworkbench.entity.ProjectMember;
import com.fpt.producerworkbench.entity.User;
import com.fpt.producerworkbench.exception.AppException;
import com.fpt.producerworkbench.exception.ErrorCode;
import com.fpt.producerworkbench.repository.LiveSessionRepository;
import com.fpt.producerworkbench.repository.ProjectMemberRepository;
import com.fpt.producerworkbench.repository.SessionParticipantRepository;
import com.fpt.producerworkbench.repository.UserRepository;
import com.fpt.producerworkbench.service.JoinRequestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class JoinRequestServiceImpl implements JoinRequestService {

    private final JoinRequestRedisService redisService;
    private final LiveSessionRepository sessionRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final SessionParticipantRepository participantRepository;
    private final UserRepository userRepository;

    private static final int REQUEST_EXPIRY_MINUTES = 5;

    @Override
    @Transactional(readOnly = true)
    public JoinRequest createJoinRequest(String sessionId, Long userId, String wsSessionId) {
        log.info("📝 Creating join request for user {} in session {}", userId, sessionId);

        // 1. Validate session exists and active
        LiveSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new AppException(ErrorCode.SESSION_NOT_FOUND));

        if (session.getStatus() != SessionStatus.ACTIVE) {
            throw new AppException(ErrorCode.SESSION_NOT_ACTIVE);
        }

        // 2. Check user is in project
        ProjectMember projectMember = projectMemberRepository
                .findByProjectIdAndUserId(session.getProject().getId(), userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_IN_PROJECT));

        // 4. Check user is not host/owner (owner join directly, no need approval)
        if (session.isHost(userId)) {
            throw new AppException(ErrorCode.OWNER_BYPASS_APPROVAL);
        }

        // 5. Get user info
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        // 6. ✅ Check if user has joined this session before (has history)
        boolean hasJoinedBefore = participantRepository.existsBySessionIdAndUserId(sessionId, userId);
        if (hasJoinedBefore) {
            log.info("✅ User {} has joined session {} before - NO approval needed", userId, sessionId);
            
            LocalDateTime now = LocalDateTime.now();
            
            // Return a special "auto-approved" request that bypasses approval
            return JoinRequest.builder()
                    .requestId("auto-approved-" + UUID.randomUUID())
                    .sessionId(sessionId)
                    .userId(userId)
                    .userName(user.getFirstName() + " " + user.getLastName())
                    .userEmail(user.getEmail())
                    .userAvatarUrl(user.getAvatarUrl())
                    .projectRole(projectMember.getProjectRole())
                    .approved(true) // ✅ Auto-approved
                    .shouldCallJoinAPI(true) // ✅ Can join directly
                    .requestedAt(now)
                    .expiresAt(now.plusYears(1)) // ✅ Set far future date (won't expire)
                    .wsSessionId(wsSessionId)
                    .build();
        }

        // 7. Check không có active request
        if (redisService.hasActiveRequest(userId)) {
            String existingRequestId = redisService.getActiveRequestId(userId);
            log.warn("⚠️ User {} already has active request {}", userId, existingRequestId);
            throw new AppException(ErrorCode.DUPLICATE_JOIN_REQUEST);
        }

        // 8. Create request
        String requestId = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = now.plusMinutes(REQUEST_EXPIRY_MINUTES);

        JoinRequest request = JoinRequest.builder()
                .requestId(requestId)
                .sessionId(sessionId)
                .userId(userId)
                .userName(user.getFirstName() + " " + user.getLastName())
                .userEmail(user.getEmail())
                .userAvatarUrl(user.getAvatarUrl())
                .projectRole(projectMember.getProjectRole())
                .requestedAt(now)
                .expiresAt(expiresAt)
                .wsSessionId(wsSessionId)
                .build();

        // 9. Save to Redis
        redisService.saveJoinRequest(request);

        log.info("✅ Join request {} created for user {} in session {}", requestId, userId, sessionId);

        return request;
    }

    @Override
    @Transactional(readOnly = true)
    public List<JoinRequest> getPendingRequests(String sessionId, Long ownerId) {
        log.debug("📋 Getting pending requests for session {} by owner {}", sessionId, ownerId);

        // Validate owner/host
        if (!canApproveRequest(sessionId, ownerId)) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        return redisService.getPendingRequests(sessionId);
    }

    @Override
    @Transactional
    public JoinRequest approveJoinRequest(String requestId, Long approverId) {
        log.info("✅ Approving join request {} by user {}", requestId, approverId);

        // 1. Get request từ Redis
        JoinRequest request = redisService.getJoinRequest(requestId);
        if (request == null) {
            throw new AppException(ErrorCode.JOIN_REQUEST_NOT_FOUND);
        }

        // 2. Validate approver là owner/host
        if (!canApproveRequest(request.getSessionId(), approverId)) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        // 3. Check request chưa expired
        if (request.isExpired()) {
            redisService.deleteJoinRequest(requestId, request.getSessionId(), request.getUserId());
            throw new AppException(ErrorCode.JOIN_REQUEST_EXPIRED);
        }

        // 4. Acquire processing lock (prevent double approve)
        if (!redisService.acquireProcessingLock(requestId)) {
            throw new AppException(ErrorCode.REQUEST_ALREADY_PROCESSED);
        }

        try {
            // 6. Delete request from Redis
            redisService.deleteJoinRequest(requestId, request.getSessionId(), request.getUserId());

            log.info("✅ Join request {} approved successfully", requestId);

            return request;

        } finally {
            // 7. Release lock
            redisService.releaseProcessingLock(requestId);
        }
    }

    @Override
    @Transactional
    public JoinRequest rejectJoinRequest(String requestId, Long approverId, String reason) {
        log.info("❌ Rejecting join request {} by user {}: {}", requestId, approverId, reason);

        // 1. Get request từ Redis
        JoinRequest request = redisService.getJoinRequest(requestId);
        if (request == null) {
            throw new AppException(ErrorCode.JOIN_REQUEST_NOT_FOUND);
        }

        // 2. Validate approver là owner/host
        if (!canApproveRequest(request.getSessionId(), approverId)) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        // 3. Delete request from Redis
        redisService.deleteJoinRequest(requestId, request.getSessionId(), request.getUserId());

        log.info("❌ Join request {} rejected", requestId);
        
        return request; // Return request info để gửi notification
    }

    @Override
    public void cancelJoinRequest(String requestId, Long userId) {
        log.info("🚫 Cancelling join request {} by user {}", requestId, userId);

        // 1. Get request từ Redis
        JoinRequest request = redisService.getJoinRequest(requestId);
        if (request == null) {
            throw new AppException(ErrorCode.JOIN_REQUEST_NOT_FOUND);
        }

        // 2. Validate user match
        if (!request.getUserId().equals(userId)) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        // 3. Delete request from Redis
        redisService.deleteJoinRequest(requestId, request.getSessionId(), request.getUserId());

        log.info("🚫 Join request {} cancelled", requestId);
    }

    @Override
    public boolean hasActiveRequest(Long userId) {
        return redisService.hasActiveRequest(userId);
    }

    @Override
    public JoinRequest getJoinRequestFromRedis(String requestId) {
        return redisService.getJoinRequest(requestId);
    }

    @Override
    public boolean canApproveRequest(String sessionId, Long userId) {
        LiveSession session = sessionRepository.findById(sessionId).orElse(null);
        if (session == null) {
            return false;
        }

        // Check là host hoặc owner
        return session.isHost(userId);
    }
}
