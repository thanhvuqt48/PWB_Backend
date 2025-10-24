package com.fpt.producerworkbench.repository;

import com.fpt.producerworkbench.entity.ChatMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, String> {

    @Query("SELECT c FROM ChatMessage c WHERE c.conversation.id = :conversationId ORDER BY c.sentAt ASC")
    List<ChatMessage> findByConversationId(String conversationId);

    Page<ChatMessage> findByConversationIdOrderBySentAtDesc(String conversationId, Pageable pageable);

}
