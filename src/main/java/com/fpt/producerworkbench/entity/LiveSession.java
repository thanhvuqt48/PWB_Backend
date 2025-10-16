package com.fpt.producerworkbench.entity;


import jakarta.persistence.*;
import lombok.*;

import java.sql.Timestamp;
import java.time.LocalDateTime;

import com.fpt.producerworkbench.common.SessionType;
import com.fpt.producerworkbench.common.SessionStatus;
@Entity
@Table(name = "live_sessions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LiveSession  extends AbstractEntity<String>{

    @ManyToOne
    @JoinColumn(name = "project_id",nullable = false)
    private Project project;

    @ManyToOne
    @JoinColumn(name = "host_id", nullable = false)
    private User host;

    @Column(nullable = false)
    private String title;

    @Column( columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)

    private SessionType sessionType;

    @Column(nullable = false, unique = true)
    private String agoraChannelName;

    @Column(nullable = false)
    private String agoraAppId;

    @Column(length = 500)
    private String agoraToken;

    private Timestamp agoraTokenExpiresAt;

    @Enumerated(EnumType.STRING)
    private SessionStatus status; // SCHEDULED, ACTIVE, PAUSED, ENDED, CANCELLED

    private Integer maxParticipants = 6;

    private Integer currentParticipants = 0;

    private Timestamp scheduledStart;

    private Timestamp actualStart;

    private LocalDateTime actualEnd;

    private Timestamp endedAt;

    private String demoScenario;
}
