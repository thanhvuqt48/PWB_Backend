package com.fpt.producerworkbench.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentStatusResponse {

    private String orderCode;
    /**
     * Trạng thái thanh toán: PENDING | SUCCESSFUL | FAILED | EXPIRED
     * - PENDING: Đang chờ thanh toán
     * - SUCCESSFUL: Đã thanh toán thành công (COMPLETED)
     * - FAILED: Thanh toán thất bại
     * - EXPIRED: Hết hạn thanh toán
     */
    private String status; // PENDING | SUCCESSFUL | FAILED | EXPIRED
    private BigDecimal amount;
    private Long projectId;
    private Long contractId;
    private Long addendumId; // nullable - chỉ có khi thanh toán phụ lục
}


