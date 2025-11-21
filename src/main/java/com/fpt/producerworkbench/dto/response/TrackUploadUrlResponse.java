package com.fpt.producerworkbench.dto.response;

import lombok.*;
import com.fasterxml.jackson.annotation.JsonProperty;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TrackUploadUrlResponse {
    @JsonProperty("trackId")
    private Long trackId;

    @JsonProperty("uploadUrl")
    private String uploadUrl;

    @JsonProperty("s3Key")
    private String s3Key;

    @JsonProperty("expiresIn")
    private Long expiresIn;
}


