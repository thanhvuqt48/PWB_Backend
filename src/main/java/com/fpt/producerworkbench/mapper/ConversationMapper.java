package com.fpt.producerworkbench.mapper;

import com.fpt.producerworkbench.common.ConversationType;
import com.fpt.producerworkbench.dto.response.ConversationCreationResponse;
import com.fpt.producerworkbench.dto.response.ParticipantInfoDetailResponse;
import com.fpt.producerworkbench.entity.Conversation;

public class ConversationMapper {

    private ConversationMapper() {
    }

    public static ConversationCreationResponse mapToConversationResponse(Conversation conversation, Long userId) {
        ConversationCreationResponse response = ConversationCreationResponse.builder()
                .id(conversation.getId())
                .conversationType(conversation.getConversationType())
                .participantHash(conversation.getParticipantHash())
                .participantInfo(conversation.getParticipants().stream()
                        .map(participantInfo -> ParticipantInfoDetailResponse.builder()
                                .userId(participantInfo.getUser().getId())
                                .username(participantInfo.getUser().getUsername())
                                .avatar(participantInfo.getUser().getAvatarUrl())
                                .build())
                        .toList())
                .createdAt(conversation.getCreatedAt())
                .milestoneChatType(conversation.getMilestoneChatType())
                .build();

        if (conversation.getConversationType() == ConversationType.GROUP) {
            response.setConversationName(conversation.getName());
            response.setConversationAvatar(conversation.getConversationAvatar());
        } else {
            if (conversation.getParticipants().size() == 1) {
                response.setConversationName(conversation.getParticipants().getFirst().getUser().getUsername());
                response.setConversationAvatar(conversation.getParticipants().getFirst().getUser().getAvatarUrl());
            } else {
                conversation.getParticipants().stream()
                        .filter(participantInfo -> !participantInfo.getUser().getId().equals(userId))
                        .findFirst().ifPresent(participantInfo -> {
                            response.setConversationName(participantInfo.getUser().getUsername());
                            response.setConversationAvatar(participantInfo.getUser().getAvatarUrl());
                        });
            }
        }

        return response;
    }
}
