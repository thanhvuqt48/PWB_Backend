package com.fpt.producerworkbench.dto.request;

import com.fpt.producerworkbench.common.SessionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateSessionRequest {

    @NotNull(message = "Project ID is required")
    private Long projectId;

    @NotBlank(message = "Title is required")
    @Size(max = 255, message = "Title cannot exceed 255 characters")
    private String title;

    @Size(max = 1000, message = "Description cannot exceed 1000 characters")
    private String description;

    @NotNull(message = "Session type is required")
    private SessionType sessionType;

    private LocalDateTime scheduledStart; // Optional, null = start now

    private List<Long> preloadedFileIds; // Optional, files to share in session

    private Boolean recordingEnabled = false;

    private Integer maxParticipants = 6;
}
