package com.fpt.producerworkbench.controller;

import com.fpt.producerworkbench.common.MessageStatus;
import com.fpt.producerworkbench.dto.request.ChatRequest;
import com.fpt.producerworkbench.dto.request.MediaAttachment;
import com.fpt.producerworkbench.dto.response.ChatResponse;
import com.fpt.producerworkbench.kafka.producer.ChatProducer;
import com.fpt.producerworkbench.service.ChatMessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
public class ChatMessageWebSocketHandler {

    private final ChatMessageService chatMessageService;
    private final SimpMessagingTemplate simpMessagingTemplate;
    private final ChatProducer chatProducer;

    @MessageMapping("/chat")
    public void handleChatMessage(@Payload ChatRequest request, Principal principal) {

        String tempId = UUID.randomUUID().toString();
        request.setTempId(tempId);
        request.setSender(principal.getName());
        chatProducer.sendChatMessage(request);

        ChatResponse responseSender = ChatResponse.builder()
                .tempId(tempId)
                .conversationId(request.getConversationId())
                .content(request.getContent())
                .me(true)
                .status(MessageStatus.SENDING)
                .mediaAttachments(request.getMediaAttachments() != null
                        ? request.getMediaAttachments().stream().map(messageMedia -> MediaAttachment.builder()
                                .mediaUrl(messageMedia.getMediaUrl())
                                .mediaName(messageMedia.getMediaName())
                                .mediaType(messageMedia.getMediaType())
                                .mediaSize(messageMedia.getMediaSize())
                                .displayOrder(messageMedia.getDisplayOrder())
                                .build())
                                .toList()
                        : List.of() )
                .messageType(request.getMessageType())
                .createdAt(LocalDateTime.now())
                .isRead(true)
                .build();

        simpMessagingTemplate.convertAndSendToUser(principal.getName(), "/queue/messages", responseSender);
    }

    @MessageMapping("/chat/{conversationId}/read/{messageId}")
    public void markAsRead(@DestinationVariable String conversationId, @DestinationVariable String messageId, Principal principal) {
        chatMessageService.markAsRead(conversationId, messageId, principal.getName());
    }

}
