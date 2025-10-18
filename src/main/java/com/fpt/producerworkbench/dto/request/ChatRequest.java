package com.fpt.producerworkbench.dto.request;

import com.fpt.producerworkbench.common.MessageType;
import com.fpt.producerworkbench.exception.validation.constraint.ConditionalNotBlank;
import com.fpt.producerworkbench.exception.validation.constraint.EnumValidator;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@ConditionalNotBlank(
        field = "mediaAttachments",
        conditionField = "messageType",
        values = {"IMAGE", "VIDEO", "AUDIO", "FILE", "STICKER"},
        message = "Media URL cannot be blank for IMAGE or VIDEO message types"
)
public class ChatRequest {

    @NotBlank(message = "Conversation cannot be blank")
    private String conversationId;
    private String sender;
    private String content;
    private String tempId;

    private List<MediaAttachment> mediaAttachments;

    @EnumValidator(enumClass = MessageType.class)
    private MessageType messageType;
}
