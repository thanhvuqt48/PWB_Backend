package com.fpt.producerworkbench.entity;

import com.fpt.producerworkbench.common.InvitationStatus;
import com.fpt.producerworkbench.common.ProjectRole;
import jakarta.persistence.*;
import lombok.*;

import java.security.Timestamp;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "session_participants",
        uniqueConstraints = @UniqueConstraint(columnNames = {"session_id", "user_id"}))
public class SessionParticipant extends AbstractEntity<Long>{

    @ManyToOne
    @JoinColumn(name = "session_id", nullable = false)
    private LiveSession session;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProjectRole participantRole;

    private Boolean canControlPlayback = false;

    private Boolean canApproveFiles = false;

    @Enumerated(EnumType.STRING)
    private InvitationStatus invitationStatus; // INVITED, ACCEPTED, DECLINED, REMOVED

    private Timestamp joinedAt;

    private Timestamp leftAt;

    private Long totalSessionTime = 0L;

    @Column(columnDefinition = "INT UNSIGNED")
    private Integer agoraUid;

    @Column(length = 500)
    private String agoraToken;

    private Timestamp agoraTokenExpiresAt;

    private Boolean isOnline = false;

    private Boolean audioEnabled = true;

    private Boolean videoEnabled = true;
}
