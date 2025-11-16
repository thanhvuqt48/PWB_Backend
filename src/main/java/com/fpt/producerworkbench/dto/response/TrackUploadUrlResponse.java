package com.fpt.producerworkbench.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

/**
 * Response DTO chứa presigned URL để upload file master
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrackUploadUrlResponse {

    @JsonProperty("trackId")
    private Long trackId;

    @JsonProperty("uploadUrl")
    private String uploadUrl;

    @JsonProperty("s3Key")
    private String s3Key;

    @JsonProperty("expiresIn")
    private Long expiresIn; // seconds
}




