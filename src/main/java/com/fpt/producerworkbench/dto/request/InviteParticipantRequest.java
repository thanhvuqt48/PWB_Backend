package com.fpt.producerworkbench.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InviteParticipantRequest {

    @NotNull(message = "User ID is required")
    private Long userId;

    private String message; // Optional invitation message
}
