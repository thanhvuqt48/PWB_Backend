package com.fpt.producerworkbench.dto.websocket;

import com.fpt.producerworkbench.common.ProjectRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JoinRequestNotification implements Serializable {
    
    private String requestId;
    private String sessionId;
    private Long userId;
    private String userName;
    private String userEmail;
    private String userAvatarUrl;
    private ProjectRole projectRole;
    private LocalDateTime requestedAt;
    private LocalDateTime expiresAt;
    private Long secondsRemaining;
    
    public static JoinRequestNotification from(JoinRequest request) {
        long secondsRemaining = java.time.Duration.between(
            LocalDateTime.now(), 
            request.getExpiresAt()
        ).getSeconds();
        
        return JoinRequestNotification.builder()
                .requestId(request.getRequestId())
                .sessionId(request.getSessionId())
                .userId(request.getUserId())
                .userName(request.getUserName())
                .userEmail(request.getUserEmail())
                .userAvatarUrl(request.getUserAvatarUrl())
                .projectRole(request.getProjectRole())
                .requestedAt(request.getRequestedAt())
                .expiresAt(request.getExpiresAt())
                .secondsRemaining(Math.max(0, secondsRemaining))
                .build();
    }
}
