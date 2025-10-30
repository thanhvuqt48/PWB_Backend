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
}
