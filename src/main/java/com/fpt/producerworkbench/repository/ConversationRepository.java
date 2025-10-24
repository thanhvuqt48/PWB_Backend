package com.fpt.producerworkbench.repository;

import com.fpt.producerworkbench.entity.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, String> {

    Optional<Conversation> findByParticipantHash(String participantHash);

    // Tìm tất cả cuộc trò chuyện mà userId này tham gia
    @Query("SELECT c FROM Conversation c JOIN c.participants p WHERE p.user.id = :userId")
    List<Conversation> findByParticipantsUserId(Long userId);

}
