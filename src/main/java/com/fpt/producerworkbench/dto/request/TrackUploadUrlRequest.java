package com.fpt.producerworkbench.dto.request;

import lombok.*;
import jakarta.validation.constraints.*;

@Setter @Getter @NoArgsConstructor @AllArgsConstructor @Builder
public class TrackUploadUrlRequest {
    @NotNull private Long projectId;
    @NotBlank private String fileName;
    @NotBlank private String mimeType;
    @NotNull  private Long sizeBytes;
    private Long expectedSizeBytes;
}