package com.fpt.producerworkbench.dto.response;

import lombok.*;

@Setter @Getter @NoArgsConstructor @AllArgsConstructor @Builder
public class TrackUploadUrlResponse {
    private String objectKey;
    private String presignedPutUrl;
    private Long   expiresInSeconds;
}