package com.fpt.producerworkbench.service.impl;

import com.fpt.producerworkbench.common.MessageStatus;
import com.fpt.producerworkbench.common.MessageType;
import com.fpt.producerworkbench.dto.request.ChatRequest;
import com.fpt.producerworkbench.dto.response.ChatResponse;
import com.fpt.producerworkbench.dto.response.PageResponse;
import com.fpt.producerworkbench.entity.*;
import com.fpt.producerworkbench.exception.AppException;
import com.fpt.producerworkbench.exception.ErrorCode;
import com.fpt.producerworkbench.mapper.ChatMessageMapper;
import com.fpt.producerworkbench.repository.ChatMessageRepository;
import com.fpt.producerworkbench.repository.ConversationRepository;
import com.fpt.producerworkbench.repository.UserRepository;
import com.fpt.producerworkbench.utils.PaginationUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j(topic = "CHAT-MESSAGE-SERVICE")
public class ChatMessageService {

    private final ChatMessageRepository chatMessageRepository;
    private final ConversationRepository conversationRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final WebSocketSessionRedisService socketSessionRedisService;

    @Transactional
    public ChatResponse createMessage(ChatRequest request) {

        User currentUser = userRepository.findByEmail(request.getSender())
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + request.getSender()));

        Conversation conversation = conversationRepository
                .findById(request.getConversationId())
                .orElseThrow(() -> new AppException(ErrorCode.CONVERSATION_NOT_FOUND));

        ChatMessage chatMessage = buildChatMessage(request, currentUser);

        List<ParticipantInfo> participantInfos = conversation.getParticipants();
        Set<Long> userIds = participantInfos.stream()
                .map(participantInfo -> participantInfo.getUser().getId())
                .collect(Collectors.toSet());

        Set<WebSocketSession> socketSessions = socketSessionRedisService.getSessionByUserIds(userIds);

        Set<Long> onlineUserIds = socketSessions.stream()
                .map(WebSocketSession::getUserId)
                .collect(Collectors.toSet());

        onlineUserIds.forEach(userId -> {
            ChatResponse userResponse = ChatMessageMapper.toWebSocketResponse(
                    chatMessage, userId, request.getTempId());

            messagingTemplate.convertAndSendToUser(userId.toString(), "/queue/messages", userResponse);
            log.info("Sent message to user: {} (me: {})", userId, userResponse.isMe());
        });

        return ChatMessageMapper.toSenderResponse(chatMessage, request.getTempId());
    }

    public PageResponse<ChatResponse> getMessageByConversationId(int page, int size, String conversationId) {
        String principalName = SecurityContextHolder.getContext()
                .getAuthentication().getName();

        Pageable pageable = PageRequest.of(page - 1, size);
        Page<ChatMessage> chatMessagePage = chatMessageRepository.findByConversationIdOrderBySentAtDesc(conversationId, pageable);

        return PaginationUtils.paginate(
                page,
                pageable.getPageSize(),
                chatMessagePage,
                chatMessage -> ChatMessageMapper.toChatResponse(chatMessage, principalName));
    }

    @Transactional
    public void markAsRead(String conversationId, String messageId) {
        String userId = SecurityContextHolder.getContext().getAuthentication().getName();
        ChatMessage chatMessage = chatMessageRepository.findById(messageId)
                .orElseThrow(() -> new AppException(ErrorCode.MESSAGE_NOT_FOUND));

        if (!chatMessage.getConversation().getId().equals(conversationId)) {
            throw new AppException(ErrorCode.MESSAGE_NOT_PART_OF_STORY);
        }

        if(Objects.equals(chatMessage.getSender().getId(), userId)) {
            log.info("Message is marked as read");
            return;
        }

        if (!chatMessage.isRead()) {
            chatMessage.setRead(true);
            chatMessageRepository.save(chatMessage);
            log.info("Marked message as read: {}", messageId);
        }
    }

    private ChatMessage buildChatMessage(ChatRequest request, User sender) {
        MessageType messageType = request.getMessageType();

        if (messageType != MessageType.TEXT && request.getMediaAttachments() == null) {
            throw new AppException(ErrorCode.MEDIA_URL_NOT_BLANK);
        }
        Conversation conversation = conversationRepository
                .findById(request.getConversationId())
                .orElseThrow(() -> new RuntimeException("Conversation not found: " + request.getConversationId()));

        ChatMessage chatMessage = new ChatMessage();
        chatMessage.setConversation(conversation);
        chatMessage.setContent(request.getContent());
        chatMessage.setSender(sender);
        chatMessage.setMessageStatus(MessageStatus.SENT);
        chatMessage.setRead(false);
        chatMessage.setIsEdited(false);
        chatMessage.setSentAt(LocalDateTime.now());

        if(messageType == MessageType.TEXT) {
            chatMessage.setMessageType(MessageType.TEXT);
        } else {
            chatMessage.setMessageType(request.getMessageType());
            List<MessageMedia> messageMedia = request.getMediaAttachments()
                    .stream()
                    .map(attachment -> {
                        Integer displayOrder = attachment.getDisplayOrder() != null
                                ? attachment.getDisplayOrder()
                                : (request.getMediaAttachments().indexOf(attachment) + 1);
                        return MessageMedia.builder()
                                .chatMessage(chatMessage)
                                .mediaUrl(attachment.getMediaUrl())
                                .mediaName(attachment.getMediaName())
                                .mediaSize(attachment.getMediaSize())
                                .mimeType(attachment.getMediaType())
                                .displayOrder(displayOrder)
                                .uploadedAt(LocalDateTime.now())
                                .build();
                    })
                    .toList();
            chatMessage.setMessageMedia(messageMedia);
        }
        chatMessageRepository.save(chatMessage);
        return chatMessage;
    }

}