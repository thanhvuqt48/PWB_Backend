package com.fpt.producerworkbench.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.*;

/**
 * Request DTO để upload version mới của một track hiện có
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrackVersionUploadRequest {

    @JsonProperty("description")
    private String description;

    @JsonProperty("voiceTagEnabled")
    @NotNull(message = "Voice tag enabled không được null")
    private Boolean voiceTagEnabled;

    @JsonProperty("voiceTagText")
    private String voiceTagText;

    @JsonProperty("contentType")
    private String contentType;

    @JsonProperty("fileSize")
    private Long fileSize;
}

