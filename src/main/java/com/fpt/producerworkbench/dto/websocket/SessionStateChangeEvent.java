package com.fpt.producerworkbench.dto.websocket;

import com.fpt.producerworkbench.common.SessionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionStateChangeEvent {

    private String sessionId;
    private SessionStatus oldStatus;
    private SessionStatus newStatus;
    private String triggeredBy; // User full name
    private Long triggeredByUserId;
    private String message; // Optional descriptive message
}
