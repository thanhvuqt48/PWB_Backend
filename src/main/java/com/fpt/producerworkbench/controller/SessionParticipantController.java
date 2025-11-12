package com.fpt.producerworkbench.controller;

import com.fpt.producerworkbench.dto.request.InviteParticipantRequest;
import com.fpt.producerworkbench.dto.request.UpdateParticipantPermissionRequest;
import com.fpt.producerworkbench.dto.response.ApiResponse;
import com.fpt.producerworkbench.dto.response.JoinSessionResponse;
import com.fpt.producerworkbench.dto.response.SessionParticipantResponse;
import com.fpt.producerworkbench.service.SessionParticipantService;
import com.fpt.producerworkbench.utils.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/sessions/{sessionId}/participants")
@RequiredArgsConstructor
public class SessionParticipantController {

    private final SessionParticipantService participantService;
    private final SecurityUtils securityUtils;

    /**
     * Invite a user to join the session
     * POST /api/sessions/{sessionId}/participants/invite
     * Only host can invite
     */
    @PostMapping("/invite")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SessionParticipantResponse> inviteParticipant(
            @PathVariable String sessionId,
            @Valid @RequestBody InviteParticipantRequest request) {

        Long invitedBy = securityUtils.getCurrentUserId();
        SessionParticipantResponse participant = participantService.inviteParticipant(sessionId, request, invitedBy);

        return ApiResponse.<SessionParticipantResponse>builder()
                .code(201)
                .message("User invited successfully")
                .result(participant)
                .build();
    }

    /**
     * Join a session (get Agora credentials)
     * POST /api/sessions/{sessionId}/join
     * 
     * For OWNER/HOST: Join directly without approval
     * For MEMBERS: Called after WebSocket approval from host
     */
    @PostMapping("/join")
    public ApiResponse<JoinSessionResponse> joinSession(@PathVariable String sessionId) {
        Long userId = securityUtils.getCurrentUserId();
        JoinSessionResponse response = participantService.joinSession(sessionId, userId);

        return ApiResponse.<JoinSessionResponse>builder()
                .message("Joined session successfully")
                .result(response)
                .build();
    }

    /**
     * Leave a session
     * POST /api/sessions/{sessionId}/leave
     */
    @PostMapping("/leave")
    public ApiResponse<Void> leaveSession(@PathVariable String sessionId) {
        Long userId = securityUtils.getCurrentUserId();
        participantService.leaveSession(sessionId, userId);

        return ApiResponse.<Void>builder()
                .message("Left session successfully")
                .build();
    }

    /**
     * Get all participants in a session
     * GET /api/sessions/{sessionId}/participants
     */
    @GetMapping
    public ApiResponse<List<SessionParticipantResponse>> getSessionParticipants(
            @PathVariable String sessionId) {

        List<SessionParticipantResponse> participants = participantService.getSessionParticipants(sessionId);

        return ApiResponse.<List<SessionParticipantResponse>>builder()
                .message("Participants retrieved successfully")
                .result(participants)
                .build();
    }

    /**
     * Get online participants in a session
     * GET /api/sessions/{sessionId}/participants/online
     */
    @GetMapping("/online")
    public ApiResponse<List<SessionParticipantResponse>> getOnlineParticipants(
            @PathVariable String sessionId) {

        List<SessionParticipantResponse> participants = participantService.getOnlineParticipants(sessionId);

        return ApiResponse.<List<SessionParticipantResponse>>builder()
                .message("Online participants retrieved successfully")
                .result(participants)
                .build();
    }

    /**
     * Update participant permissions
     * PUT /api/sessions/{sessionId}/participants/{userId}/permissions
     * Only host can update permissions
     */
    @PutMapping("/{userId}/permissions")
    public ApiResponse<SessionParticipantResponse> updateParticipantPermissions(
            @PathVariable String sessionId,
            @PathVariable Long userId,
            @Valid @RequestBody UpdateParticipantPermissionRequest request) {

        Long updatedBy = securityUtils.getCurrentUserId();
        SessionParticipantResponse participant = participantService.updateParticipantPermissions(
                sessionId, userId, request, updatedBy);

        return ApiResponse.<SessionParticipantResponse>builder()
                .message("Permissions updated successfully")
                .result(participant)
                .build();
    }

    /**
     * Refresh Agora token for participant
     * POST /api/sessions/{sessionId}/participants/token/refresh
     */
    @PostMapping("/token/refresh")
    public ApiResponse<String> refreshAgoraToken(@PathVariable String sessionId) {
        Long userId = securityUtils.getCurrentUserId();
        String newToken = participantService.refreshAgoraToken(sessionId, userId);

        return ApiResponse.<String>builder()
                .message("Token refreshed successfully")
                .result(newToken)
                .build();
    }

    /**
     * Remove participant from session (kick)
     * DELETE /api/sessions/{sessionId}/participants/{userId}
     * Only host can remove participants
     */
    @DeleteMapping("/{userId}")
    public ApiResponse<Void> removeParticipant(
            @PathVariable String sessionId,
            @PathVariable Long userId) {

        Long removedBy = securityUtils.getCurrentUserId();
        participantService.removeParticipant(sessionId, userId, removedBy);

        return ApiResponse.<Void>builder()
                .message("Participant removed successfully")
                .build();
    }
}
