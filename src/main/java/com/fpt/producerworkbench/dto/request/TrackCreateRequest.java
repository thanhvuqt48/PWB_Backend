package com.fpt.producerworkbench.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

/**
 * Request DTO để tạo track mới
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrackCreateRequest {

    @JsonProperty("name")
    @NotBlank(message = "Tên bài nhạc không được để trống")
    private String name;

    @JsonProperty("description")
    private String description;

    @JsonProperty("version")
    @NotBlank(message = "Version không được để trống")
    private String version;

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




