package com.fpt.producerworkbench.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Setter
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AIContextRequest {

    @NotBlank(message = "Query is required")
    @Size(max = 500, message = "Query must not exceed 500 characters")
    String query;

    // Optional: Current page/context from frontend
    String currentPage; // e.g., "/projects", "/tracks"

    // Optional: User's role context (auto-filled from JWT)
    String userRole;

    // Optional: Include related guides in response?
    @Builder.Default
    Boolean includeRelatedGuides = true;

    // Optional: Max guides to return
    @Builder.Default
    Integer maxGuides = 3;
    
    // Session ID for conversation memory (auto-generated if not provided)
    String sessionId;
}
