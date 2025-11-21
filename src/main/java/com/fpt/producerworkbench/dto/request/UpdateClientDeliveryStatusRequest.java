package com.fpt.producerworkbench.dto.request;

import com.fpt.producerworkbench.common.ClientDeliveryStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO cho việc cập nhật status của client delivery
 * Dùng khi client từ chối, yêu cầu chỉnh sửa hoặc chấp nhận
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateClientDeliveryStatusRequest {
    
    /**
     * Status mới (REJECTED, REQUEST_EDIT hoặc ACCEPTED)
     */
    @NotNull(message = "Status không được để trống")
    private ClientDeliveryStatus status;
    
    /**
     * Lý do từ chối hoặc yêu cầu chỉnh sửa
     * Bắt buộc nếu status = REQUEST_EDIT
     */
    private String reason;
}

