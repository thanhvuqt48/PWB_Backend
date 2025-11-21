package com.fpt.producerworkbench.dto.response;

import com.fpt.producerworkbench.common.TrackStatus;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class TrackListItemResponse {
    Long id;
    String fileName;
    String mimeType;
    Long sizeBytes;
    TrackStatus status;
    String lyricsText;
    String aiSuggestions;
}
