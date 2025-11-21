package com.fpt.producerworkbench.dto.event;

import lombok.*;

@Getter @AllArgsConstructor
public class AudioUploadedEvent {
    private final Long trackId;
}