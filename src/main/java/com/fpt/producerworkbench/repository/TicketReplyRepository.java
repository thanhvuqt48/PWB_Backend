package com.fpt.producerworkbench.repository;

import com.fpt.producerworkbench.entity.TicketReply;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TicketReplyRepository extends JpaRepository<TicketReply, Long> {
    List<TicketReply> findByTicketIdOrderByCreatedAtAsc(Long ticketId);
    Optional<TicketReply> findFirstByTicketIdOrderByCreatedAtAsc(Long ticketId);
}