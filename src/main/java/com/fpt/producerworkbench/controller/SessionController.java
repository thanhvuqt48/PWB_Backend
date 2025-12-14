package com.fpt.producerworkbench.controller;

import com.fpt.producerworkbench.common.ProcessingStatus;
import com.fpt.producerworkbench.common.SessionStatus;
import com.fpt.producerworkbench.dto.request.CreateSessionRequest;
import com.fpt.producerworkbench.dto.request.UpdateSessionRequest;
import com.fpt.producerworkbench.dto.response.*;
import com.fpt.producerworkbench.exception.AppException;
import com.fpt.producerworkbench.exception.ErrorCode;
import com.fpt.producerworkbench.service.ClientDeliveryService;
import com.fpt.producerworkbench.service.LiveSessionService;
import com.fpt.producerworkbench.service.MilestoneService;
import com.fpt.producerworkbench.service.TrackMilestoneService;
import com.fpt.producerworkbench.utils.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/sessions")
@RequiredArgsConstructor
@Slf4j
public class SessionController {

    private final LiveSessionService sessionService;
    private final SecurityUtils securityUtils;
    private final TrackMilestoneService trackMilestoneService;
    private final ClientDeliveryService clientDeliveryService;
    private final MilestoneService milestoneService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<LiveSessionResponse> createSession(@Valid @RequestBody CreateSessionRequest request) {
        Long hostId = securityUtils.getCurrentUserId();
        LiveSessionResponse session = sessionService.createSession(request, hostId);

        return ApiResponse.<LiveSessionResponse>builder()
                .code(201)
                .message("Session created successfully")
                .result(session)
                .build();
    }

    @GetMapping("/{sessionId}")
    public ApiResponse<LiveSessionResponse> getSession(@PathVariable String sessionId) {
        LiveSessionResponse session = sessionService.getSessionById(sessionId);

        return ApiResponse.<LiveSessionResponse>builder()
                .message("Session retrieved successfully")
                .result(session)
                .build();
    }

    @PutMapping("/{sessionId}")
    public ApiResponse<LiveSessionResponse> updateSession(
            @PathVariable String sessionId,
            @Valid @RequestBody UpdateSessionRequest request) {
        Long userId = securityUtils.getCurrentUserId();
        LiveSessionResponse session = sessionService.updateSession(sessionId, request, userId);

        return ApiResponse.<LiveSessionResponse>builder()
                .message("Session updated successfully")
                .result(session)
                .build();
    }

    @DeleteMapping("/{sessionId}")
    public ApiResponse<Void> deleteSession(@PathVariable String sessionId) {
        Long userId = securityUtils.getCurrentUserId();
        sessionService.deleteSession(sessionId, userId);

        return ApiResponse.<Void>builder()
                .message("Session deleted successfully")
                .build();
    }

    @GetMapping("/projects/{projectId}")
    public ApiResponse<PageResponse<LiveSessionResponse>> getSessionsByProject(
            @PathVariable Long projectId,
            @RequestParam(required = false) String status,  // ← Đổi sang String để tránh enum parse error
            @RequestParam(required = false, defaultValue = "0") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {

        try {

            // Validate page & size
            if (page < 0) page = 0;
            if (size < 1 || size > 100) size = 20;

            // Parse status safely
            SessionStatus sessionStatus = null;
            if (status != null && !status.trim().isEmpty()) {
                try {
                    sessionStatus = SessionStatus.valueOf(status.toUpperCase());
                } catch (IllegalArgumentException e) {
                    // Invalid status → ignore it (treat as null)
                    log.warn("Invalid session status: {}", status);
                }
            }
            Long currentUserId = securityUtils.getCurrentUserId();
            Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
            Page<LiveSessionResponse> sessions = sessionService.getSessionsByProject(
                    projectId,
                    sessionStatus,
                    pageable,
                    currentUserId
            );

            return ApiResponse.<PageResponse<LiveSessionResponse>>builder()
                    .message("Sessions retrieved successfully")
                    .result(PageResponse.fromPage(sessions))
                    .build();

        } catch (Exception e) {
            log.error("Error getting sessions for project {}: {}", projectId, e.getMessage(), e);
            return ApiResponse.<PageResponse<LiveSessionResponse>>builder()
                    .code(1001)
                    .message("Failed to retrieve sessions: " + e.getMessage())
                    .build();
        }
    }


    @PostMapping("/{sessionId}/start")
    public ApiResponse<LiveSessionResponse> startSession(@PathVariable String sessionId) {
        Long userId = securityUtils.getCurrentUserId(); // ✅ Instance method
        LiveSessionResponse session = sessionService.startSession(sessionId, userId);

        return ApiResponse.<LiveSessionResponse>builder()
                .message("Session started successfully")
                .result(session)
                .build();
    }

    @PostMapping("/{sessionId}/end")
    public ApiResponse<SessionSummaryResponse> endSession(@PathVariable String sessionId) {
        Long userId = securityUtils.getCurrentUserId();
        SessionSummaryResponse summary = sessionService.endSession(sessionId, userId);

        return ApiResponse.<SessionSummaryResponse>builder()
                .message("Session ended successfully")
                .result(summary)
                .build();
    }

    @PostMapping("/{sessionId}/cancel")
    public ApiResponse<LiveSessionResponse> cancelSession(
            @PathVariable String sessionId,
            @RequestParam(required = false) String reason) {

        Long userId = securityUtils.getCurrentUserId();
        LiveSessionResponse session = sessionService.cancelSession(sessionId, userId, reason);

        return ApiResponse.<LiveSessionResponse>builder()
                .message("Session cancelled successfully")
                .result(session)
                .build();
    }

    @PostMapping("/{sessionId}/invite-more")
    public ApiResponse<LiveSessionResponse> inviteMoreMembers(
            @PathVariable String sessionId,
            @Valid @RequestBody com.fpt.producerworkbench.dto.request.InviteMoreMembersRequest request) {

        Long userId = securityUtils.getCurrentUserId();
        LiveSessionResponse session = sessionService.inviteMoreMembers(sessionId, request, userId);

        return ApiResponse.<LiveSessionResponse>builder()
                .message("Members invited successfully")
                .result(session)
                .build();
    }

    @GetMapping("/{sessionId}/available-members")
    public ApiResponse<List<AvailableMemberResponse>> getAvailableMembers(
            @PathVariable String sessionId) {

        Long userId = securityUtils.getCurrentUserId();
        List<com.fpt.producerworkbench.dto.response.AvailableMemberResponse> members = 
                sessionService.getAvailableMembers(sessionId, userId);

        return ApiResponse.<List<com.fpt.producerworkbench.dto.response.AvailableMemberResponse>>builder()
                .message("Available members retrieved successfully")
                .result(members)
                .build();
    }

    /**
     * Lấy danh sách cột mốc kèm danh sách nhạc từ phòng nội bộ và phòng khách hàng
     * File nhạc trả về sẽ tự động có voice tag nếu track có bật voice tag,
     * nếu không thì là bản gốc (giống như hiển thị trong phòng nội bộ/khách hàng)
     * 
     * @param sessionId ID của live session
     * @return Danh sách milestones kèm tracks từ cả 2 phòng (INTERNAL và CLIENT)
     */
    @GetMapping("/{sessionId}/milestones")
    public ApiResponse<SessionMilestonesWithTracksResponse> getMilestonesWithTracks(
            @PathVariable String sessionId) {
        
        log.info("Getting milestones with tracks for session {}", sessionId);
        
        // Validate session exists
        LiveSessionResponse session = sessionService.getSessionById(sessionId);
        if (session.getProjectId() == null) {
            throw new AppException(ErrorCode.PROJECT_NOT_FOUND);
        }
        
        // Get authentication for services
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        
        // Get milestones by project
        List<MilestoneListResponse> milestones = milestoneService.getAllMilestonesByProject(
                session.getProjectId(), auth);
        
        // Build response with tracks for each milestone
        List<MilestoneWithTracksResponse> milestonesWithTracks = milestones.stream()
                .map(milestone -> {
                    // Get internal tracks
                    List<SessionTrackResponse> internalTracks = new ArrayList<>();
                    try {
                        List<TrackResponse> internalTrackList = trackMilestoneService.getTracksByMilestone(auth, milestone.getId());
                        internalTracks = internalTrackList.stream()
                                .filter(t -> t.getHlsPlaybackUrl() != null 
                                        && t.getProcessingStatus() == ProcessingStatus.READY)
                                .map(t -> SessionTrackResponse.builder()
                                        .trackId(t.getId())
                                        .trackName(t.getName())
                                        .version(t.getVersion())
                                        .hlsPlaybackUrl(t.getHlsPlaybackUrl())
                                        .duration(t.getDuration())
                                        .roomType("INTERNAL")
                                        .voiceTagEnabled(t.getVoiceTagEnabled())
                                        .build())
                                .collect(Collectors.toList());
                    } catch (Exception e) {
                        log.warn("Error getting internal tracks for milestone {}: {}", milestone.getId(), e.getMessage());
                    }
                    
                    // Get client tracks
                    List<SessionTrackResponse> clientTracks = new ArrayList<>();
                    try {
                        List<ClientTrackResponse> clientTrackList = clientDeliveryService.getClientTracks(auth, milestone.getId());
                        clientTracks = clientTrackList.stream()
                                .filter(ct -> ct.getTrack() != null 
                                        && ct.getTrack().getHlsPlaybackUrl() != null
                                        && ct.getTrack().getProcessingStatus() == ProcessingStatus.READY)
                                .map(ct -> SessionTrackResponse.builder()
                                        .trackId(ct.getTrack().getId())
                                        .trackName(ct.getTrack().getName())
                                        .version(ct.getTrack().getVersion())
                                        .hlsPlaybackUrl(ct.getTrack().getHlsPlaybackUrl())
                                        .duration(ct.getTrack().getDuration())
                                        .roomType("CLIENT")
                                        .voiceTagEnabled(ct.getTrack().getVoiceTagEnabled())
                                        .build())
                                .collect(Collectors.toList());
                    } catch (Exception e) {
                        log.warn("Error getting client tracks for milestone {}: {}", milestone.getId(), e.getMessage());
                    }
                    
                    return MilestoneWithTracksResponse.builder()
                            .id(milestone.getId())
                            .title(milestone.getTitle())
                            .description(milestone.getDescription())
                            .status(milestone.getStatus())
                            .sequence(milestone.getSequence())
                            .projectTitle(milestone.getProjectTitle())
                            .createdAt(milestone.getCreatedAt())
                            .updatedAt(milestone.getUpdatedAt())
                            .internalTracks(internalTracks)
                            .clientTracks(clientTracks)
                            .build();
                })
                .collect(Collectors.toList());
        
        log.info("Found {} milestones with tracks for session {}", milestonesWithTracks.size(), sessionId);
        
        SessionMilestonesWithTracksResponse response = SessionMilestonesWithTracksResponse.builder()
                .milestones(milestonesWithTracks)
                .build();
        
        return ApiResponse.<SessionMilestonesWithTracksResponse>builder()
                .code(HttpStatus.OK.value())
                .message("Lấy danh sách cột mốc và nhạc thành công")
                .result(response)
                .build();
    }

}
