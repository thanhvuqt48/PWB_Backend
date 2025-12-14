package com.fpt.producerworkbench.entity;

import com.fpt.producerworkbench.common.ConversationType;
import com.fpt.producerworkbench.common.MilestoneChatType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "conversations")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
public class Conversation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "name")
    private String name; // Tên hội thoại, áp dụng cho nhóm

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, name = "conversation_type")
    private ConversationType conversationType;

    @Column(unique = true, columnDefinition = "TEXT")
    private String participantHash;

    @Column(name = "conversation_avatar")
    private String conversationAvatar;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "milestone_id")
    private Milestone milestone;

    @Enumerated(EnumType.STRING)
    @Column(name = "milestone_chat_type")
    private MilestoneChatType milestoneChatType; // INTERNAL hoặc CLIENT - chỉ áp dụng cho group chat của milestone

    /**
     * KHÔNG khởi tạo = new ArrayList<>() tại field level.
     * Để Hibernate quản lý collection, tránh lỗi "Found shared references to a collection".
     */
    @OneToMany(mappedBy = "conversation", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ParticipantInfo> participants;

    @OneToMany(mappedBy = "conversation", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ChatMessage> chatMessages;

    public List<ParticipantInfo> getParticipants() {
        if (participants == null) {
            participants = new ArrayList<>();
        }
        return participants;
    }

    public List<ChatMessage> getChatMessages() {
        if (chatMessages == null) {
            chatMessages = new ArrayList<>();
        }
        return chatMessages;
    }

    @Column(name = "last_message_at")
    private LocalDateTime lastMessageAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

}
