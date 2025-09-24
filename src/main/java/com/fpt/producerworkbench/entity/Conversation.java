package com.fpt.producerworkbench.entity;

import com.fpt.producerworkbench.common.ConversationType;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "conversations")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Conversation extends AbstractEntity<Long>{

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id") // Nullable
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "milestone_id") // Nullable
    private Milestone milestone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ConversationType type;

    private String title;

}