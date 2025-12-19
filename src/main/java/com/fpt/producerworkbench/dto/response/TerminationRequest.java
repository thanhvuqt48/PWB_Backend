package com.fpt.producerworkbench.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request để chấm dứt hợp đồng
 * - terminatedBy: tự động xác định từ user đăng nhập (CLIENT hoặc OWNER)
 * - reason: optional, có thể null hoặc empty
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TerminationRequest {
    
    private String reason; // Lý do chấm dứt (optional)
}


