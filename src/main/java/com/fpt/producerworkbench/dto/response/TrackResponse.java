package com.fpt.producerworkbench.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fpt.producerworkbench.common.ProcessingStatus;
import com.fpt.producerworkbench.common.TrackStatus;
import lombok.*;

import java.util.Date;

/**
 * Response DTO cho Track
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrackResponse {

    @JsonProperty("id")
    private Long id;

    @JsonProperty("name")
    private String name;

    @JsonProperty("description")
    private String description;

    @JsonProperty("version")
    private String version;

    @JsonProperty("rootTrackId")
    private Long rootTrackId;

    @JsonProperty("parentTrackId")
    private Long parentTrackId;

    @JsonProperty("milestoneId")
    private Long milestoneId;

    @JsonProperty("userId")
    private Long userId;

    @JsonProperty("userName")
    private String userName;

    @JsonProperty("userAvatarUrl")
    private String userAvatarUrl;

    @JsonProperty("voiceTagEnabled")
    private Boolean voiceTagEnabled;

    @JsonProperty("voiceTagText")
    private String voiceTagText;

    @JsonProperty("status")
    private TrackStatus status;

    @JsonProperty("reason")
    private String reason;

    @JsonProperty("processingStatus")
    private ProcessingStatus processingStatus;

    @JsonProperty("errorMessage")
    private String errorMessage;

    @JsonProperty("contentType")
    private String contentType;

    @JsonProperty("fileSize")
    private Long fileSize;

    @JsonProperty("duration")
    private Integer duration;

    @JsonProperty("hlsPlaybackUrl")
    private String hlsPlaybackUrl;

    @JsonProperty("createdAt")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private Date createdAt;

    @JsonProperty("updatedAt")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private Date updatedAt;
}




