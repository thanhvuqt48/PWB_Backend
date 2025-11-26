package com.fpt.producerworkbench.service;

import com.fpt.producerworkbench.dto.request.ChatRequest;
import com.fpt.producerworkbench.dto.response.ChatResponse;
import com.fpt.producerworkbench.dto.response.PageResponse;

import java.util.Map;

public interface ChatMessageService {
    ChatResponse createMessage(ChatRequest request);
    
    PageResponse<ChatResponse> getMessageByConversationId(int page, int size, String conversationId);
    
    void markAsRead(String conversationId, String messageId, String userEmail);
    
    Map<String, Boolean> getOnlineStatusForConversation(String conversationId);
}

