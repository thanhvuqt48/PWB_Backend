package com.fpt.producerworkbench.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RejectWithdrawalRequest {
    
    @NotBlank(message = "Rejection reason is required")
    private String rejectionReason;
}

