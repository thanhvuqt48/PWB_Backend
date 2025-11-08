package com.fpt.producerworkbench.entity;

import com.fpt.producerworkbench.common.SessionStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.LastModifiedBy;

import java.time.LocalDateTime;
import java.util.Date;

@Entity
@Table(name = "live_sessions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LiveSession {

    @Id
    @GeneratedValue(generator = "uuid2")
    @GenericGenerator(name = "uuid2", strategy = "uuid2")
    @Column(name = "id", length = 36)
    private String id;
    // ========== Relationships ==========
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "host_id", nullable = false)
    private User host; // Producer (OWNER of project)

    // ========== Basic Info ==========

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SessionStatus status = SessionStatus.SCHEDULED;

    @Column(name = "is_public")
    private Boolean isPublic = true; // true = PUBLIC (anyone can see), false = PRIVATE (invited only)

    // ========== Agora Config ==========

    @Column(name = "agora_channel_name", nullable = false, unique = true, length = 64)
    private String agoraChannelName;

    @Column(name = "agora_app_id", nullable = false)
    private String agoraAppId;

    // ========== Participants ==========

    @Column(name = "current_participants", nullable = false)
    private Integer currentParticipants = 0;

    // ========== Timing ==========

    @Column(name = "scheduled_start")
    private LocalDateTime scheduledStart;

    @Column(name = "actual_start")
    private LocalDateTime actualStart;

    @Column(name = "actual_end")
    private LocalDateTime actualEnd;

    // ========== Playback State ==========

    @Column(name = "current_playing_file_id")
    private Long currentPlayingFileId;

    @Column(name = "playback_started_at")
    private LocalDateTime playbackStartedAt;

    // ========== Demo/Testing ==========

    @Column(name = "demo_scenario")
    private String demoScenario;

    @CreatedBy
    private String createdBy;
    @LastModifiedBy
    private String updatedBy;
    @CreationTimestamp
    private Date createdAt;
    @UpdateTimestamp
    private Date updatedAt;


    // ========== Business Methods ==========

    public boolean isActive() {
        return this.status == SessionStatus.ACTIVE;
    }

    public boolean canJoin() {
        return this.status == SessionStatus.ACTIVE;
    }

    public void incrementParticipants() {
        this.currentParticipants++;
    }

    public void decrementParticipants() {
        if (this.currentParticipants > 0) {
            this.currentParticipants--;
        }
    }

    public boolean isHost(Long userId) {
        return this.host.getId().equals(userId);
    }
}