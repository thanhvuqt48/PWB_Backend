package com.fpt.producerworkbench.dto.websocket;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlaybackEvent {

    private String action; // PLAY, PAUSE, SEEK, STOP, NEXT, PREVIOUS
    private Long fileId;
    private String fileName;
    private String fileUrl;
    private Long position; // Current position in milliseconds
    private Long duration; // Total duration in milliseconds
    private Float playbackRate; // 1.0 = normal speed
    private Long triggeredByUserId;
    private String triggeredByUserName;
    
    // ✅ NEW: Additional metadata for member sync
    private String roomType; // "INTERNAL" or "CLIENT"
    private Boolean voiceTagEnabled;
    private String version; // v1, v2, ...
    private String artist; // Milestone title or artist name
    private Long trackId; // Alias for fileId
    private String trackName; // Alias for fileName
    private String hlsPlaybackUrl; // Alias for fileUrl
}
