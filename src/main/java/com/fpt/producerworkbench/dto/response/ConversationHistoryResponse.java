package com.fpt.producerworkbench.dto.response;

import lombok.*;

import java.util.List;

@Setter
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ConversationHistoryResponse {
    
    private String sessionId;
    private List<MessageDTO> messages;
    private Integer totalMessages;
    
    @Setter
    @Getter
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class MessageDTO {
        private String role;      // "user" or "assistant"
        private String content;   // Message text
    }
}
