package com.fpt.producerworkbench.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddBankAccountRequest {
    
    @NotNull(message = "Bank ID is required")
    private Long bankId;
    
    @NotBlank(message = "Account number is required")
    @Size(max = 50, message = "Account number must not exceed 50 characters")
    private String accountNumber;
    
    @NotBlank(message = "Account holder name is required")
    @Size(max = 255, message = "Account holder name must not exceed 255 characters")
    private String accountHolderName;
    
    @NotBlank(message = "OTP is required")
    private String otp;
}

