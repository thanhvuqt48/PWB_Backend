package com.fpt.producerworkbench.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
@FieldDefaults(level = lombok.AccessLevel.PRIVATE)
public class TrackUploadDirectResponse {
    Long trackId;
    String objectKey;
    String mimeType;
    long sizeBytes;
}
