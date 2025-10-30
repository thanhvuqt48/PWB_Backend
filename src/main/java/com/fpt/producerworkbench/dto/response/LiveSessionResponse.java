package com.fpt.producerworkbench.dto.response;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fpt.producerworkbench.common.SessionStatus;
import com.fpt.producerworkbench.common.SessionType;
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

    // Project & Host
    private Long projectId;
    private String projectName;
    private Long hostId;
    private String hostName;

    // Basic Info
    private String title;
    private String description;
    private SessionType sessionType;
    private SessionStatus status;

    // Agora Info
    private String agoraChannelName;

    // Participants
    private Integer maxParticipants;
    private Integer currentParticipants;

    // Timing
    private LocalDateTime scheduledStart;
    private LocalDateTime actualStart;
    private LocalDateTime actualEnd;

    // Recording
    private Boolean recordingEnabled;
    private String recordingUrl;

    // Playback
    private Long currentPlayingFileId;

    // Metadata
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Participants list (optional, for detail view)
    private List<SessionParticipantResponse> participants;
}
