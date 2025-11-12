package com.fpt.producerworkbench.dto.websocket;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
@JsonIgnoreProperties(ignoreUnknown = true) // ✅ Ignore unknown fields like "expired"
public class JoinRequest implements Serializable {
    
    private String requestId;
    private String sessionId;
    private Long userId;
    private String userName;
    private String userEmail;
    private String userAvatarUrl;
    private ProjectRole projectRole; // ProjectRole của user trong project
    private LocalDateTime requestedAt;
    private LocalDateTime expiresAt; // Auto-expire sau 5 phút
    private String wsSessionId; // WebSocket session ID để track connection
    
    // ✅ For auto-approval (when user has joined before)
    private Boolean approved; // true if auto-approved (user has history)
    private Boolean shouldCallJoinAPI; // true if user can join directly
    
    @JsonIgnore // ✅ Don't serialize this method as "expired" field
    public boolean isExpired() {
        if (expiresAt == null) {
            return false; // Auto-approved requests don't expire
        }
        return LocalDateTime.now().isAfter(expiresAt);
    }
}
