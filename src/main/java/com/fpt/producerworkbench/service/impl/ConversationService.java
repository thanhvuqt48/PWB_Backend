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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

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

        List<Long> participantIds = request.getParticipantIds();

        if(!participantIds.contains(sender.getId())) {
            participantIds.add(sender.getId());
        }

        List<User> participants = userRepository.findAllById(participantIds);

        if(participants.size() != participantIds.size()) {
            throw new AppException(ErrorCode.PARTICIPANT_INVALID);
        }

        String participantHash = participantIds.size() > 1
                ? String.join("_", participantIds.toString())
                : participantIds.getFirst().toString();

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

}
