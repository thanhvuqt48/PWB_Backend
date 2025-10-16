package com.fpt.producerworkbench.entity;

import com.fpt.producerworkbench.common.SessionStatus;
import com.fpt.producerworkbench.common.SessionType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "live_sessions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LiveSession extends AbstractEntity<String> {

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
    private SessionType sessionType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SessionStatus status = SessionStatus.SCHEDULED;

    // ========== Agora Config ==========

    @Column(name = "agora_channel_name", nullable = false, unique = true, length = 64)
    private String agoraChannelName;

    @Column(name = "agora_app_id", nullable = false)
    private String agoraAppId;

    // ========== Participants ==========

    @Column(name = "max_participants", nullable = false)
    private Integer maxParticipants = 6;

    @Column(name = "current_participants", nullable = false)
    private Integer currentParticipants = 0;

    // ========== Timing ==========

    @Column(name = "scheduled_start")
    private LocalDateTime scheduledStart;

    @Column(name = "actual_start")
    private LocalDateTime actualStart;

    @Column(name = "actual_end")
    private LocalDateTime actualEnd;

    // ========== Recording ==========

    @Column(name = "recording_enabled")
    private Boolean recordingEnabled = false;

    @Column(name = "recording_url", length = 500)
    private String recordingUrl;

    // ========== Playback State ==========

    @Column(name = "current_playing_file_id")
    private Long currentPlayingFileId;

    @Column(name = "playback_started_at")
    private LocalDateTime playbackStartedAt;

    // ========== Demo/Testing ==========

    @Column(name = "demo_scenario")
    private String demoScenario;

    // ========== Business Methods ==========

    public boolean isActive() {
        return this.status == SessionStatus.ACTIVE;
    }

    public boolean canJoin() {
        return this.status == SessionStatus.ACTIVE
                && this.currentParticipants < this.maxParticipants;
    }

    public void incrementParticipants() {
        this.currentParticipants++;
    }

    public void decrementParticipants() {
        if (this.currentParticipants > 0) {
            this.currentParticipants--;
        }
    }

    public boolean isFull() {
        return this.currentParticipants >= this.maxParticipants;
    }

    public boolean isHost(Long userId) {
        return this.host.getId().equals(userId);
    }
}