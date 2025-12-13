package com.fpt.producerworkbench.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fpt.producerworkbench.common.ContractStatus;
import com.fpt.producerworkbench.common.TerminationType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Response khi chấm dứt hợp đồng thành công
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TerminationResponse {
    
    private Long terminationId;
    private Long contractId;
    private ContractStatus newStatus;
    private TerminationType terminationType; // BEFORE_DAY_20 hoặc AFTER_DAY_20
    
    // Số tiền
    private BigDecimal compensationAmount; // Tổng số tiền đền bù (dùng cho Client)
    private BigDecimal teamCompensation; // Tổng đền bù Team (gross) - dùng cho Owner
    private BigDecimal ownerCompensation; // Đền bù Owner (gross) - dùng cho Owner
    private BigDecimal clientRefund; // Hoàn cho Client
    private BigDecimal taxDeducted; // Tổng thuế đã khấu trừ - dùng cho Owner
    
    // Thanh toán 2 lần (nếu sau ngày 20)
    private Boolean hasSecondPayment;
    private LocalDate secondPaymentDate;
    private BigDecimal secondPaymentAmount;
    
    // Thông tin Owner Compensation Payment (nếu Owner chấm dứt)
    private Long ownerCompensationPaymentId;
    private String paymentUrl; // Link cho Owner chuyển tiền
    private String paymentOrderCode;
    
    private String message;
}


