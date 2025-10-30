package com.fpt.producerworkbench.entity;

import com.fpt.producerworkbench.common.InvitationStatus;
import com.fpt.producerworkbench.common.ParticipantRole;
import jakarta.persistence.*;
import lombok.*;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;

@Entity
@Table(name = "session_participants",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_session_user",
                columnNames = {"session_id", "user_id"}
        ))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SessionParticipant extends AbstractEntity<Long> {

    // ========== Relationships ==========

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private LiveSession session;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // ========== Role & Status ==========

    @Enumerated(EnumType.STRING)
    @Column(name = "participant_role", nullable = false)
    private ParticipantRole participantRole;

    @Enumerated(EnumType.STRING)
    @Column(name = "invitation_status", nullable = false)
    private InvitationStatus invitationStatus = InvitationStatus.PENDING;

    // ========== Agora Credentials ==========

    @Column(name = "agora_uid")
    private Integer agoraUid;

    @Column(name = "agora_token", length = 500)
    private String agoraToken;

    @Column(name = "agora_token_expires_at")
    private LocalDateTime agoraTokenExpiresAt;

    // ========== Session Timing ==========

    @Column(name = "invited_at")
    private LocalDateTime invitedAt;

    @Column(name = "joined_at")
    private Instant joinedAt;  // ✅ Changed to Instant

    @Column(name = "left_at")
    private Instant leftAt;    // ✅ Changed to Instant

    @Column(name = "total_session_time")
    private Long totalSessionTime = 0L; // seconds

    // ========== Online Status ==========

    @Column(name = "is_online", nullable = false)
    private Boolean isOnline = false;

    @Column(name = "audio_enabled")
    private Boolean audioEnabled = true;

    @Column(name = "video_enabled")
    private Boolean videoEnabled = true;

    // ========== Permissions ==========

    @Column(name = "can_share_audio")
    private Boolean canShareAudio = true;

    @Column(name = "can_share_video")
    private Boolean canShareVideo = true;

    @Column(name = "can_control_playback")
    private Boolean canControlPlayback = false;

    @Column(name = "can_approve_files")
    private Boolean canApproveFiles = false;

    // ========== Business Methods ==========

    public boolean isOwner() {
        return this.participantRole == ParticipantRole.OWNER;
    }

    public boolean isPending() {
        return this.invitationStatus == InvitationStatus.PENDING;
    }

    public boolean hasAccepted() {
        return this.invitationStatus == InvitationStatus.ACCEPTED;
    }

    public boolean canPublish() {
        return this.participantRole != ParticipantRole.OBSERVER;
    }

    public void acceptInvitation() {
        this.invitationStatus = InvitationStatus.ACCEPTED;
    }

    public void declineInvitation() {
        this.invitationStatus = InvitationStatus.DECLINED;
    }

    // ✅ FIXED: Use Instant.now() directly
    public void markAsOnline() {
        this.isOnline = true;
        if (this.joinedAt == null) {
            this.joinedAt = Instant.now();  // ✅ Fixed
        }
    }

    // ✅ FIXED: Use Instant.now() directly + null-safe calculation
    public void markAsOffline() {
        this.isOnline = false;
        this.leftAt = Instant.now();  // ✅ Fixed - no conversion needed

        // ✅ Null-safe calculation
        if (this.joinedAt != null && this.leftAt != null) {
            long sessionDuration = Duration.between(this.joinedAt, this.leftAt).toSeconds();

            if (this.totalSessionTime == null) {
                this.totalSessionTime = 0L;
            }

            this.totalSessionTime += sessionDuration;
        }
    }
}
