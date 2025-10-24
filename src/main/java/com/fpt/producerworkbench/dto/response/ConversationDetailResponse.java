package com.fpt.producerworkbench.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ConversationDetailResponse {
    private String id;
    private String conversationName;
    private String conversationType;
    private String participantHash;
    private String lastMessage;
    private LocalDateTime lastMessageTime;
}
