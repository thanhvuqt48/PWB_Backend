package com.fpt.producerworkbench.entity;

import com.fpt.producerworkbench.common.MessageStatus;
import com.fpt.producerworkbench.common.MessageType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "chat_messages")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
@NamedEntityGraph(
        name = "ChatMessage.withMedia",
        attributeNodes = @NamedAttributeNode("messageMedia")
)
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id", nullable = false)
    private Conversation conversation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    @Column(columnDefinition = "TEXT")
    private String content;

    @OneToMany(mappedBy = "chatMessage", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private List<MessageMedia> messageMedia;

    @Enumerated(EnumType.STRING)
    private MessageType messageType;

    @Enumerated(EnumType.STRING)
    private MessageStatus messageStatus;

    @Column(nullable = false)
    private Boolean isEdited;

    @Column(nullable = false)
    private LocalDateTime sentAt;

    private boolean isRead;

    private LocalDateTime editedAt;

}
