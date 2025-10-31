package com.fpt.producerworkbench.dto.websocket;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SessionEventMessage {

    private String eventType; // SESSION_STARTED, PARTICIPANT_JOINED, CHAT_MESSAGE, etc.
    private String sessionId;
    private LocalDateTime timestamp;
    private Object payload;
}
