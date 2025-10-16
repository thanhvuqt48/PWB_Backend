package com.fpt.producerworkbench.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fpt.producerworkbench.common.InvitationStatus;
import com.fpt.producerworkbench.common.ParticipantRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SessionParticipantResponse {
    private Long id;
    private String sessionId;
    // User info
    private Long userId;
    private String userName;
    private String userEmail;
    private String userAvatarUrl;

    // Role & Status
    private ParticipantRole participantRole;
    private InvitationStatus invitationStatus;

    // Online status
    private Boolean isOnline;
    private Boolean audioEnabled;
    private Boolean videoEnabled;

    // Permissions
    private Boolean canShareAudio;
    private Boolean canShareVideo;
    private Boolean canControlPlayback;
    private Boolean canApproveFiles;

    // Timing
    private LocalDateTime invitedAt;
    private LocalDateTime joinedAt;
    private LocalDateTime leftAt;
    private Long totalSessionTime; // in seconds
}
