package com.fpt.producerworkbench.mapper;

import com.fpt.producerworkbench.common.MessageStatus;
import com.fpt.producerworkbench.dto.request.MediaAttachment;
import com.fpt.producerworkbench.dto.response.ChatResponse;
import com.fpt.producerworkbench.entity.ChatMessage;

import java.util.List;
import java.util.Objects;

public class ChatMessageMapper {

    private ChatMessageMapper() {}

    public static ChatResponse toBaseChatResponse(ChatMessage chatMessage) {
        return ChatResponse.builder()
                .id(chatMessage.getId())
                .conversationId(chatMessage.getConversation().getId())
                .content(chatMessage.getContent())
                .status(MessageStatus.SENT)
                .mediaAttachments(chatMessage.getMessageMedia() != null
                        ? chatMessage.getMessageMedia().stream().map(messageMedia -> MediaAttachment.builder()
                                .mediaUrl(messageMedia.getMediaUrl())
                                .mediaName(messageMedia.getMediaName())
                                .mediaType(messageMedia.getMimeType())
                                .mediaSize(messageMedia.getMediaSize())
                                .displayOrder(messageMedia.getDisplayOrder())
                                .build())
                        .toList()
                        : List.of() )
                .messageType(chatMessage.getMessageType())
                .createdAt(chatMessage.getSentAt())
                .build();
    }

    public static ChatResponse toChatResponse(ChatMessage chatMessage, String principalName) {
        ChatResponse chatResponse = toBaseChatResponse(chatMessage);
        chatResponse.setMe(Objects.equals(principalName, chatMessage.getSender().getEmail()));
        chatResponse.setRead(chatMessage.isRead());

        return chatResponse;
    }

    public static ChatResponse toWebSocketResponse(ChatMessage chatMessage, String userId, String tempId) {
        boolean isMe = Objects.equals(userId, chatMessage.getSender().getEmail());

        ChatResponse response = toBaseChatResponse(chatMessage);
        response.setTempId(isMe ? tempId : null);
        response.setMe(isMe);
        response.setRead(isMe);

        return response;
    }

    public static ChatResponse toSenderResponse(ChatMessage chatMessage, String tempId) {
        return ChatResponse.builder()
                .id(chatMessage.getId())
                .tempId(tempId)
                .conversationId(chatMessage.getConversation().getId())
                .me(true)
                .content(chatMessage.getContent())
                .status(MessageStatus.SENT)
                .mediaAttachments(buildMediaAttachments(chatMessage))
                .messageType(chatMessage.getMessageType())
                .createdAt(chatMessage.getSentAt())
                .isRead(true)
                .build();
    }

    private static List<MediaAttachment> buildMediaAttachments(ChatMessage chatMessage) {
        if (chatMessage.getMessageMedia() == null || chatMessage.getMessageMedia().isEmpty()) {
            return List.of();
        }

        return chatMessage.getMessageMedia().stream()
                .map(messageMedia -> MediaAttachment.builder()
                        .mediaUrl(messageMedia.getMediaUrl())
                        .mediaName(messageMedia.getMediaName())
                        .mediaType(messageMedia.getMimeType())
                        .mediaSize(messageMedia.getMediaSize())
                        .displayOrder(messageMedia.getDisplayOrder())
                        .build())
                .toList();
    }

}
