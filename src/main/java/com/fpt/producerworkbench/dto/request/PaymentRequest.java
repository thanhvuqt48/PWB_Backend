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
public class PaymentRequest {
    
    @NotBlank(message = "Return URL is required")
    private String returnUrl;
    
    @NotBlank(message = "Cancel URL is required")
    private String cancelUrl;

    /**
     * milestoneId: dùng khi contract paymentType = MILESTONE để chỉ định cột mốc cần thanh toán.
     * Optional cho paymentType = FULL hoặc thanh toán phụ lục.
     */
    private Long milestoneId;
}
