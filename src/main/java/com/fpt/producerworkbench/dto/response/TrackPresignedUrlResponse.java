package com.fpt.producerworkbench.dto.response;

import lombok.*;
import com.fasterxml.jackson.annotation.JsonProperty;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TrackPresignedUrlResponse {
    @JsonProperty("objectKey")
    private String objectKey;
    
    @JsonProperty("presignedPutUrl")
    private String presignedPutUrl;
    
    @JsonProperty("expiresInSeconds")
    private Long expiresInSeconds;
}

