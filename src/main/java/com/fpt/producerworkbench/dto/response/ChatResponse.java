package com.fpt.producerworkbench.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fpt.producerworkbench.common.MessageStatus;
import com.fpt.producerworkbench.common.MessageType;
import com.fpt.producerworkbench.dto.request.MediaAttachment;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChatResponse {
    private String id;
    private String tempId;
    private String conversationId;
    private boolean me;
    private String content;
    private MessageStatus status;
    private boolean isRead;
    private List<MediaAttachment> mediaAttachments;
    private MessageType messageType;
    private LocalDateTime createdAt;

}
