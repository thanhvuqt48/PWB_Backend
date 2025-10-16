package com.fpt.producerworkbench.service;
import com.fpt.producerworkbench.common.ParticipantRole;
import com.fpt.producerworkbench.dto.request.InviteParticipantRequest;
import com.fpt.producerworkbench.dto.request.UpdateParticipantPermissionRequest;
import com.fpt.producerworkbench.dto.response.JoinSessionResponse;
import com.fpt.producerworkbench.dto.response.SessionParticipantResponse;

import java.util.List;


public interface SessionParticipantService {
    /**
     * Invite a user to join a session
     *
     * @param sessionId Session ID
     * @param request Invitation request
     * @param invitedBy User ID of inviter (must be session host)
     * @return Created participant
     */
    SessionParticipantResponse inviteParticipant(
            String sessionId,
            InviteParticipantRequest request,
            Long invitedBy
    );

    /**
     * User joins a session (generates Agora credentials)
     *
     * @param sessionId Session ID
     * @param userId User ID
     * @return Join session response with Agora token and credentials
     */
    JoinSessionResponse joinSession(String sessionId, Long userId);

    /**
     * User leaves a session
     *
     * @param sessionId Session ID
     * @param userId User ID
     */
    void leaveSession(String sessionId, Long userId);

    /**
     * Get all participants in a session
     *
     * @param sessionId Session ID
     * @return List of participants
     */
    List<SessionParticipantResponse> getSessionParticipants(String sessionId);

    /**
     * Get online participants in a session
     *
     * @param sessionId Session ID
     * @return List of online participants
     */
    List<SessionParticipantResponse> getOnlineParticipants(String sessionId);

    /**
     * Update participant permissions
     *
     * @param sessionId Session ID
     * @param userId User ID
     * @param request Permission update request
     * @param updatedBy User ID of updater (must be host)
     * @return Updated participant
     */
    SessionParticipantResponse updateParticipantPermissions(
            String sessionId,
            Long userId,
            UpdateParticipantPermissionRequest request,
            Long updatedBy
    );

    /**
     * Refresh Agora token for participant
     *
     * @param sessionId Session ID
     * @param userId User ID
     * @return New token
     */
    String refreshAgoraToken(String sessionId, Long userId);

    /**
     * Check if user can join session
     *
     * @param sessionId Session ID
     * @param userId User ID
     * @return true if can join
     */
    boolean canUserJoinSession(String sessionId, Long userId);

    /**
     * Determine participant role based on project role
     *
     * @param projectId Project ID
     * @param userId User ID
     * @return Participant role
     */
    ParticipantRole determineParticipantRole(Long projectId, Long userId);
}
