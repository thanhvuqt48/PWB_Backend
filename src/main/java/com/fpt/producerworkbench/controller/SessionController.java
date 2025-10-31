package com.fpt.producerworkbench.controller;

import com.fpt.producerworkbench.common.SessionStatus;
import com.fpt.producerworkbench.dto.request.CreateSessionRequest;
import com.fpt.producerworkbench.dto.response.ApiResponse;
import com.fpt.producerworkbench.dto.response.LiveSessionResponse;
import com.fpt.producerworkbench.dto.response.PageResponse;
import com.fpt.producerworkbench.dto.response.SessionSummaryResponse;
import com.fpt.producerworkbench.service.LiveSessionService;
import com.fpt.producerworkbench.utils.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/sessions")
@RequiredArgsConstructor
@Slf4j
public class SessionController {

    private final LiveSessionService sessionService;
    private final SecurityUtils securityUtils;

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

    @PostMapping("/{sessionId}/pause")
    public ApiResponse<LiveSessionResponse> pauseSession(@PathVariable String sessionId) {
        Long userId = securityUtils.getCurrentUserId();
        LiveSessionResponse session = sessionService.pauseSession(sessionId, userId);

        return ApiResponse.<LiveSessionResponse>builder()
                .message("Session paused successfully")
                .result(session)
                .build();
    }

    @PostMapping("/{sessionId}/resume")
    public ApiResponse<LiveSessionResponse> resumeSession(@PathVariable String sessionId) {
        Long userId = securityUtils.getCurrentUserId();
        LiveSessionResponse session = sessionService.resumeSession(sessionId, userId);

        return ApiResponse.<LiveSessionResponse>builder()
                .message("Session resumed successfully")
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
}
