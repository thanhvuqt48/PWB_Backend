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
public class ChatMessage {

    private String messageId;
    private String sessionId;
    private Long senderId;
    private String senderName;
    private String senderAvatarUrl;
    private String content;
    private String type; // TEXT, SYSTEM, FILE, EMOJI
    private LocalDateTime timestamp;

    // Optional metadata
    private String fileUrl;
    private String fileName;
    private Long fileSize;
}
