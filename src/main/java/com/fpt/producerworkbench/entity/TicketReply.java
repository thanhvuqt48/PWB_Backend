package com.fpt.producerworkbench.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "ticket_replies")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketReply extends AbstractEntity<Long>{

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_id", nullable = false)
    private Ticket ticket;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @ElementCollection
    @CollectionTable(name = "ticket_reply_attachments", joinColumns = @JoinColumn(name = "reply_id"))
    @Column(name = "s3_key")
    private List<String> attachmentKeys = new ArrayList<>();

}