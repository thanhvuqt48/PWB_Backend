package com.fpt.producerworkbench.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.fpt.producerworkbench.common.TerminatedBy;
import com.fpt.producerworkbench.common.TerminationStatus;
import com.fpt.producerworkbench.common.TerminationType;

/**
 * Chi tiết thông tin chấm dứt hợp đồng
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TerminationDetailResponse {
    
    private Long terminationId;
    private Long contractId;
    private TerminatedBy terminatedBy;
    private TerminationType terminationType;
    private TerminationStatus status;
    private LocalDateTime terminationDate;
    
    // Tài chính
    private BigDecimal totalContractAmount;
    private BigDecimal totalTeamCompensation;
    private BigDecimal totalOwnerCompensation;
    private BigDecimal totalClientRefund;
    private BigDecimal totalTaxDeducted;
    
    // Thuế
    private BigDecimal originalTax;
    private BigDecimal actualTax;
    private BigDecimal refundedTax;
    
    private String reason;
    private String notes;
}


