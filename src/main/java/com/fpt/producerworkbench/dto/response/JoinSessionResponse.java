package com.fpt.producerworkbench.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class JoinSessionResponse {

    private String sessionId;

    // Agora credentials
    private String channelName;
    private String appId;
    private String token;
    private Integer uid;
    private String role; // "Role_Publisher" or "Role_Subscriber"

    private Integer expiresIn; // seconds

    // WebSocket URL (optional)
    private String wsUrl;
}
