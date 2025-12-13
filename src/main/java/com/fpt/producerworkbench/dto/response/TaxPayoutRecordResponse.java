package com.fpt.producerworkbench.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.fpt.producerworkbench.common.PayoutSource;
import com.fpt.producerworkbench.common.PayoutStatus;

/**
 * Response cho Tax Payout Record
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaxPayoutRecordResponse {
    
    private Long id;
    private Long userId;
    private String userFullName;
    private String userCccd;
    
    private PayoutSource payoutSource;
    private Long contractId;
    private Long milestoneId;
    
    private BigDecimal grossAmount;
    private BigDecimal taxAmount;
    private BigDecimal netAmount;
    private BigDecimal taxRate;
    
    private LocalDate payoutDate;
    private Integer taxPeriodYear;
    private Integer taxPeriodMonth;
    private Integer taxPeriodQuarter;
    
    private PayoutStatus status;
    private Boolean isTaxDeclared;
    private LocalDate taxDeclarationDate;
    
    private String description;
}


