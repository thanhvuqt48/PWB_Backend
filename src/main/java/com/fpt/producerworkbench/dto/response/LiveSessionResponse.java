package com.fpt.producerworkbench.dto.response;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fpt.producerworkbench.common.SessionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LiveSessionResponse {
    private String id;
    private Long currentUserId;
    // Project & Host
    private Long projectId;
    private String projectName;
    private Long hostId;
    private String hostName;

    // Basic Info
    private String title;
    private String description;
    private SessionStatus status;

    // Agora Info
    private String agoraChannelName;

    // Participants
    private Integer currentParticipants;
    private Boolean isPublic; // true = PUBLIC (anyone can join), false = PRIVATE (invited only)

    // Timing
    private LocalDateTime scheduledStart;
    private LocalDateTime actualStart;
    private LocalDateTime actualEnd;

    // Playback
    private Long currentPlayingFileId;

    // Metadata
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Participants list (optional, for detail view)
    private List<SessionParticipantResponse> participants;
}
