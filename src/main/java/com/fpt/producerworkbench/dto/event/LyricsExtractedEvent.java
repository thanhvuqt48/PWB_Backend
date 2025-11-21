package com.fpt.producerworkbench.dto.event;

import lombok.*;

@Getter @AllArgsConstructor
public class LyricsExtractedEvent {
    private final Long trackId;
    private final String lyricsText;
}