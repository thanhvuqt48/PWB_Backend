package com.fpt.producerworkbench.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fpt.producerworkbench.common.TrackStatus;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

/**
 * Request DTO để cập nhật thông tin track
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrackUpdateRequest {

    @JsonProperty("name")
    @NotBlank(message = "Tên bài nhạc không được để trống")
    private String name;

    @JsonProperty("description")
    private String description;

    @JsonProperty("version")
    @NotBlank(message = "Version không được để trống")
    private String version;

    @JsonProperty("voiceTagEnabled")
    private Boolean voiceTagEnabled;

    @JsonProperty("voiceTagText")
    private String voiceTagText;

    @JsonProperty("status")
    private TrackStatus status;
}




