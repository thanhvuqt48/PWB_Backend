package com.fpt.producerworkbench.service.impl;

import com.fpt.producerworkbench.common.ConversationType;
import com.fpt.producerworkbench.dto.request.ConversationCreationRequest;
import com.fpt.producerworkbench.dto.response.ConversationCreationResponse;
import com.fpt.producerworkbench.entity.Conversation;
import com.fpt.producerworkbench.entity.ParticipantInfo;
import com.fpt.producerworkbench.entity.User;
import com.fpt.producerworkbench.exception.AppException;
import com.fpt.producerworkbench.exception.ErrorCode;
import com.fpt.producerworkbench.mapper.ConversationMapper;
import com.fpt.producerworkbench.repository.ConversationRepository;
import com.fpt.producerworkbench.repository.UserRepository;
import com.fpt.producerworkbench.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j(topic = "CONVERSATION-SERVICE")
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final UserRepository userRepository;

    public ConversationCreationResponse create(ConversationCreationRequest request) {

        String email = SecurityUtils.getCurrentUserLogin()
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHENTICATED));

        User sender = userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        LinkedHashSet<Long> participantIdSet = new LinkedHashSet<>(request.getParticipantIds());
        participantIdSet.add(sender.getId());

        List<Long> participantIds = new ArrayList<>(participantIdSet);

        List<User> participants = userRepository.findAllById(participantIds);

        if(participants.size() != participantIds.size()) {
            throw new AppException(ErrorCode.PARTICIPANT_INVALID);
        }

        String participantHash = buildParticipantHash(participantIds);

        if(request.getConversationType() == ConversationType.PRIVATE) {
            Optional<Conversation> conversation = conversationRepository.findByParticipantHash(participantHash);
            if(conversation.isPresent()) {
                log.info("Conversation existed");
                return ConversationMapper.mapToConversationResponse(conversation.get(), sender.getId());
            }
        }

        List<ParticipantInfo> participantInfos = participants.stream()
                .map(user -> ParticipantInfo.builder()
                        .user(user)
                        .conversation(null)
                        .joinedAt(LocalDateTime.now())
                        .build())
                .toList();

        Conversation conversation = Conversation.builder()
                .name(request.getConversationName())
                .conversationType(request.getConversationType())
                .participantHash(participantHash)
                .participants(participantInfos)
                .lastMessageAt(LocalDateTime.now())
                .build();

        participantInfos.forEach(participantInfo ->
                participantInfo.setConversation(conversation));

        conversationRepository.save(conversation);

        return ConversationMapper.mapToConversationResponse(conversation, sender.getId());
    }

    @Transactional
    public ConversationCreationResponse addMembersToGroup(String conversationId, List<Long> memberIds) {
        if (memberIds == null || memberIds.isEmpty()) {
            throw new AppException(ErrorCode.BAD_REQUEST);
        }

        String email = SecurityUtils.getCurrentUserLogin()
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHENTICATED));

        User currentUser = userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new AppException(ErrorCode.CONVERSATION_NOT_FOUND));

        if (conversation.getConversationType() != ConversationType.GROUP) {
            throw new AppException(ErrorCode.CONVERSATION_NOT_GROUP);
        }

        boolean isCurrentUserParticipant = conversation.getParticipants().stream()
                .anyMatch(participantInfo -> participantInfo.getUser().getId().equals(currentUser.getId()));

        if (!isCurrentUserParticipant) {
            throw new AppException(ErrorCode.FORBIDDEN);
        }

        Set<Long> existingMemberIds = conversation.getParticipants().stream()
                .map(participantInfo -> participantInfo.getUser().getId())
                .collect(Collectors.toSet());

        Set<Long> uniqueDesiredMemberIds = memberIds.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        uniqueDesiredMemberIds.removeAll(existingMemberIds);

        if (uniqueDesiredMemberIds.isEmpty()) {
            throw new AppException(ErrorCode.CONVERSATION_MEMBER_ALREADY_EXISTS);
        }

        List<User> newMembers = userRepository.findAllById(uniqueDesiredMemberIds);
        if (newMembers.size() != uniqueDesiredMemberIds.size()) {
            throw new AppException(ErrorCode.PARTICIPANT_INVALID);
        }

        List<ParticipantInfo> participantInfos = newMembers.stream()
                .map(user -> ParticipantInfo.builder()
                        .user(user)
                        .conversation(conversation)
                        .joinedAt(LocalDateTime.now())
                        .build())
                .toList();

        conversation.getParticipants().addAll(participantInfos);

        List<Long> updatedParticipantIds = conversation.getParticipants().stream()
                .map(participantInfo -> participantInfo.getUser().getId())
                .toList();

        conversation.setParticipantHash(buildParticipantHash(updatedParticipantIds));

        conversationRepository.save(conversation);

        return ConversationMapper.mapToConversationResponse(conversation, currentUser.getId());
    }

    public List<ConversationCreationResponse> myConversation() {
        String email = SecurityUtils.getCurrentUserLogin()
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHENTICATED));

        User currentUser = userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        List<Conversation> conversations = conversationRepository.findByParticipantsUserId(currentUser.getId());

        return conversations.stream()
                .map(conversation -> ConversationMapper.mapToConversationResponse(conversation, currentUser.getId()))
                .toList();
    }

    public void deleteConversation(String id) {
        var conversation = conversationRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.CONVERSATION_NOT_FOUND));
        conversationRepository.delete(conversation);
        log.info("Conversation deleted");
    }

    private String buildParticipantHash(List<Long> participantIds) {
        return participantIds.stream()
                .sorted()
                .map(String::valueOf)
                .collect(Collectors.joining("_"));
    }

}
