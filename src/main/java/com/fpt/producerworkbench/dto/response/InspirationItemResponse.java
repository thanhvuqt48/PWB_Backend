package com.fpt.producerworkbench.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fpt.producerworkbench.common.InspirationType;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;

@Setter @Getter @Builder
@NoArgsConstructor @AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class InspirationItemResponse {
    Long id;
    InspirationType type;
    String title;
    String noteContent;
    String fileKey;
    String viewUrl;
    String mimeType;
    Long sizeBytes;

    @JsonProperty("uploaderId")
    Long uploaderId;

    @JsonProperty("uploaderName")
    String uploaderName;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    LocalDateTime createdAt;
}