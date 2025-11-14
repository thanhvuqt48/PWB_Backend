package com.fpt.producerworkbench.service.impl;

import com.fpt.producerworkbench.common.MessageStatus;
import com.fpt.producerworkbench.common.MessageType;
import com.fpt.producerworkbench.dto.event.NotificationEvent;
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
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j(topic = "CHAT-MESSAGE-SERVICE")
public class ChatMessageService {

    private static final String NOTIFICATION_TOPIC = "notification-delivery";
    private static final String FRONTEND_URL = "http://localhost:5173"; // Có thể config từ application.yml

    private final ChatMessageRepository chatMessageRepository;
    private final ConversationRepository conversationRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final WebSocketSessionRedisService socketSessionRedisService;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Transactional
    public ChatResponse createMessage(ChatRequest request) {

        User currentUser = userRepository.findByEmail(request.getSender())
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + request.getSender()));

        Conversation conversation = conversationRepository
                .findById(request.getConversationId())
                .orElseThrow(() -> new AppException(ErrorCode.CONVERSATION_NOT_FOUND));

        ChatMessage chatMessage = buildChatMessage(request, currentUser);

        List<ParticipantInfo> participantInfos = conversation.getParticipants();
        Set<String> userIds = participantInfos.stream()
                .map(participantInfo -> participantInfo.getUser().getEmail())
                .collect(Collectors.toSet());

        Set<WebSocketSession> socketSessions = socketSessionRedisService.getSessionByUserIds(userIds);

        Set<String> onlineUserIds = socketSessions.stream()
                .map(WebSocketSession::getUserId)
                .collect(Collectors.toSet());

        onlineUserIds.forEach(userId -> {
            ChatResponse userResponse = ChatMessageMapper.toWebSocketResponse(
                    chatMessage, userId, request.getTempId());

            messagingTemplate.convertAndSendToUser(userId.toString(), "/queue/messages", userResponse);
            log.info("Sent message to user: {} (me: {})", userId, userResponse.isMe());
        });

        Set<String> offlineUserIds = userIds.stream()
                .filter(userId -> !onlineUserIds.contains(userId))
                .filter(userId -> !userId.equals(currentUser.getEmail()))
                .collect(Collectors.toSet());

        if (!offlineUserIds.isEmpty()) {
            sendEmailNotificationToOfflineUsers(offlineUserIds, currentUser, chatMessage, conversation);
        }

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
    public void markAsRead(String conversationId, String messageId, String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        ChatMessage chatMessage = chatMessageRepository.findById(messageId)
                .orElseThrow(() -> new AppException(ErrorCode.MESSAGE_NOT_FOUND));

        if (!chatMessage.getConversation().getId().equals(conversationId)) {
            throw new AppException(ErrorCode.MESSAGE_NOT_PART_OF_STORY);
        }

        if(Objects.equals(chatMessage.getSender().getId(), user.getId())) {
            log.info("Message is marked as read");
            return;
        }

        if (!chatMessage.isRead()) {
            chatMessage.setRead(true);
            chatMessageRepository.save(chatMessage);
            log.info("Marked message as read: {}", messageId);
        }
    }

    public Map<String, Boolean> getOnlineStatusForConversation(String conversationId) {

        Conversation conversation = conversationRepository
                .findById(conversationId)
                .orElseThrow(() -> new AppException(ErrorCode.CONVERSATION_NOT_FOUND));

        Set<String> userEmails = conversation.getParticipants().stream()
                .map(p -> p.getUser().getEmail())
                .collect(Collectors.toSet());

        log.info("Checking online status for conversation {}: participants = {}", conversationId, userEmails);

        Set<WebSocketSession> onlineSessions = socketSessionRedisService.getSessionByUserIds(userEmails);
        Set<String> onlineUserEmails = onlineSessions.stream()
                .map(WebSocketSession::getUserId)
                .collect(Collectors.toSet());

        log.info("Online users from Redis: {}", onlineUserEmails);

        Map<String, Boolean> onlineStatus = userEmails.stream()
                .collect(Collectors.toMap(
                    email -> email,
                    onlineUserEmails::contains
                ));

        log.info("Retrieved online status for conversation {}: {} users, {} online. Status map: {}",
                conversationId, userEmails.size(), onlineUserEmails.size(), onlineStatus);

        return onlineStatus;
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

    private void sendEmailNotificationToOfflineUsers(Set<String> offlineUserEmails,
                                                      User sender,
                                                      ChatMessage chatMessage,
                                                      Conversation conversation) {
        try {
            List<User> offlineUsers = userRepository.findAllByEmailIn(new ArrayList<>(offlineUserEmails));

            for (User offlineUser : offlineUsers) {
                try {
                    String messageContent = prepareMessageContentForEmail(chatMessage);

                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
                    String formattedTime = chatMessage.getSentAt().format(formatter);

                    String conversationLink = FRONTEND_URL + "/chat/" + conversation.getId();

                    String senderAvatar = sender.getAvatarUrl() != null
                            ? sender.getAvatarUrl()
                            : "https://via.placeholder.com/48";

                    NotificationEvent event = NotificationEvent.builder()
                            .recipient(offlineUser.getEmail())
                            .subject("Tin nhắn mới từ " + sender.getFullName())
                            .templateCode("new-message-notification")
                            .param(Map.of(
                                    "recipientName", offlineUser.getFullName() != null ? offlineUser.getFullName() : offlineUser.getEmail(),
                                    "senderName", sender.getFullName() != null ? sender.getFullName() : sender.getEmail(),
                                    "senderAvatar", senderAvatar,
                                    "messageContent", messageContent,
                                    "messageTime", formattedTime,
                                    "conversationLink", conversationLink
                            ))
                            .build();

                    kafkaTemplate.send(NOTIFICATION_TOPIC, event);
                    log.info("Đã gửi email notification cho user offline: {}", offlineUser.getEmail());

                } catch (Exception e) {
                    log.error("Lỗi khi gửi email notification cho user {}: {}", offlineUser.getEmail(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("Lỗi khi xử lý email notification cho offline users: {}", e.getMessage());
        }
    }

    private String prepareMessageContentForEmail(ChatMessage chatMessage) {
        String content = chatMessage.getContent();

        if (chatMessage.getMessageType() == MessageType.TEXT) {
            if (content != null && content.length() > 200) {
                return content.substring(0, 200) + "...";
            }
            return content != null ? content : "";
        }

        if (chatMessage.getMessageType() == MessageType.IMAGE) {
            return "📷 Đã gửi một hình ảnh" + (content != null && !content.isEmpty() ? ": " + content : "");
        }

        if (chatMessage.getMessageType() == MessageType.VIDEO) {
            return "🎥 Đã gửi một video" + (content != null && !content.isEmpty() ? ": " + content : "");
        }

        if (chatMessage.getMessageType() == MessageType.AUDIO) {
            return "🎵 Đã gửi một file âm thanh" + (content != null && !content.isEmpty() ? ": " + content : "");
        }

        if (chatMessage.getMessageType() == MessageType.FILE) {
            return "📎 Đã gửi một file" + (content != null && !content.isEmpty() ? ": " + content : "");
        }

        return content != null ? content : "Đã gửi một tin nhắn";
    }

}