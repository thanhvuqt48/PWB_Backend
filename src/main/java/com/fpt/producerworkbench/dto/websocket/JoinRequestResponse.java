package com.fpt.producerworkbench.dto.websocket;

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
public class JoinRequestResponse implements Serializable {
    
    private String requestId;
    private String sessionId;
    private Long userId; // ✅ Add userId
    private Boolean approved;
    private String reason;
    private Boolean shouldCallJoinAPI; // Frontend biết có nên call REST API join không
    private Boolean shouldRetry; // Có cho phép retry request không
    private LocalDateTime timestamp; // ✅ Add timestamp
    
    public static JoinRequestResponse approved(String requestId, String sessionId) {
        return JoinRequestResponse.builder()
                .requestId(requestId)
                .sessionId(sessionId)
                .approved(true)
                .reason("Your request has been approved")
                .shouldCallJoinAPI(true)
                .shouldRetry(false)
                .build();
    }
    
    public static JoinRequestResponse rejected(String requestId, String sessionId, String reason) {
        return JoinRequestResponse.builder()
                .requestId(requestId)
                .sessionId(sessionId)
                .approved(false)
                .reason(reason)
                .shouldCallJoinAPI(false)
                .shouldRetry(false)
                .build();
    }
    
    public static JoinRequestResponse expired(String requestId, String sessionId) {
        return JoinRequestResponse.builder()
                .requestId(requestId)
                .sessionId(sessionId)
                .approved(false)
                .reason("Request expired (no response from host)")
                .shouldCallJoinAPI(false)
                .shouldRetry(true)
                .build();
    }
}
