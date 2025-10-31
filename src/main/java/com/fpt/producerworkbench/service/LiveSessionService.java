package com.fpt.producerworkbench.service;

import com.fpt.producerworkbench.entity.LiveSession;

import com.fpt.producerworkbench.common.SessionStatus;
import com.fpt.producerworkbench.dto.request.CreateSessionRequest;
import com.fpt.producerworkbench.dto.response.LiveSessionResponse;
import com.fpt.producerworkbench.dto.response.SessionSummaryResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
public interface LiveSessionService {

     //Create a new live session
    LiveSessionResponse createSession(CreateSessionRequest request, Long hostId);
    //Start a session
    LiveSessionResponse startSession(String sessionId, Long userId);
     //Pause a session
    LiveSessionResponse pauseSession(String sessionId, Long userId);
     //Resume a paused session
    LiveSessionResponse resumeSession(String sessionId, Long userId);
     //End a session
    SessionSummaryResponse endSession(String sessionId, Long userId);
     //Cancel a scheduled session
    LiveSessionResponse cancelSession(String sessionId, Long userId, String reason);
     //Get session by ID
    LiveSessionResponse getSessionById(String sessionId);
     //Get all sessions by project
    Page<LiveSessionResponse> getSessionsByProject(Long projectId, SessionStatus status, Pageable pageable,Long currentUserId);
     //Get sessions hosted by user
    Page<LiveSessionResponse> getSessionsByHost(Long hostId, Pageable pageable);
     //Check if project has active session
    boolean hasActiveSession(Long projectId);

}
