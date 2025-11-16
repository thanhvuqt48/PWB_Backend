package com.fpt.producerworkbench.service;

import com.fpt.producerworkbench.dto.websocket.JoinRequest;

import java.util.List;

public interface JoinRequestService {
    
    /**
     * Tạo join request (member xin vào phòng)
     */
    JoinRequest createJoinRequest(String sessionId, Long userId, String wsSessionId);
    
    /**
     * Lấy tất cả pending requests của session (for owner)
     */
    List<JoinRequest> getPendingRequests(String sessionId, Long ownerId);
    
    /**
     * Owner approve request
     */
    JoinRequest approveJoinRequest(String requestId, Long approverId);
    
    /**
     * Owner reject request
     */
    JoinRequest rejectJoinRequest(String requestId, Long approverId, String reason);
    
    /**
     * Member tự cancel request
     */
    void cancelJoinRequest(String requestId, Long userId);
    
    /**
     * Check user có active request không
     */
    boolean hasActiveRequest(Long userId);
    
    /**
     * Get join request từ Redis (for internal use)
     */
    JoinRequest getJoinRequestFromRedis(String requestId);
    
    /**
     * Validate user có quyền approve/reject không (owner/host)
     */
    boolean canApproveRequest(String sessionId, Long userId);
}
