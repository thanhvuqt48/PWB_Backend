package com.fpt.producerworkbench.dto.request;

import com.fpt.producerworkbench.common.ConversationType;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;

import java.util.List;

@Getter
public class ConversationCreationRequest {

    @NotNull(message = "Conversation type cannot be null")
    private ConversationType conversationType;

    @Size(max = 100, message = "Conversation name cannot exceed 100 characters")
    private String conversationName;

    @NotEmpty(message = "Participant cannot be null")
    @Size(min = 1,  message = "At least 1 participants are required")
    private List<Long> participantIds;
}
