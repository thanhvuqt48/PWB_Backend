package com.fpt.producerworkbench.service;

import com.fpt.producerworkbench.dto.request.ConversationCreationRequest;
import com.fpt.producerworkbench.dto.response.ConversationCreationResponse;

import java.util.List;

public interface ConversationService {
    ConversationCreationResponse create(ConversationCreationRequest request);
    
    ConversationCreationResponse addMembersToGroup(String conversationId, List<Long> memberIds);
    
    List<ConversationCreationResponse> myConversation();
    
    void deleteConversation(String id);
}

