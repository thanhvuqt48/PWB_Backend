package com.fpt.producerworkbench.dto.response;

import com.fpt.producerworkbench.common.TrackStatus;
import lombok.*;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TrackSuggestionResponse {
    private Long trackId;
    private TrackStatus status;
    private String lyricsText;
    private String aiSuggestions;
    private String transcribeJobName;
}
