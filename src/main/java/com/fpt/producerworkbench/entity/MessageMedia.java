package com.fpt.producerworkbench.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "message_media")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
public class MessageMedia {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne
    @JoinColumn(name = "chat_message_id", nullable = false)
    private ChatMessage chatMessage;

    @Column(nullable = false)
    private String mediaUrl;

    private String mediaName;

    private Long mediaSize;

    private String mimeType;

    private Integer displayOrder; // Thứ tự hiển thị

    private LocalDateTime uploadedAt;
}

