package com.fpt.producerworkbench.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request để chấm dứt hợp đồng
 * - terminatedBy: tự động xác định từ user đăng nhập (CLIENT hoặc OWNER)
 * - reason: optional, có thể null hoặc empty
 * - returnUrl, cancelUrl: optional, dùng cho Owner compensation payment
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TerminationRequest {
    
    private String reason; // Lý do chấm dứt (optional)
    
    /**
     * Return URL sau khi thanh toán thành công (cho Owner compensation payment)
     * Nếu không có, sẽ dùng default từ payosProperties
     */
    private String returnUrl;
    
    /**
     * Cancel URL khi user hủy thanh toán (cho Owner compensation payment)
     * Nếu không có, sẽ dùng default từ payosProperties
     */
    private String cancelUrl;
}


