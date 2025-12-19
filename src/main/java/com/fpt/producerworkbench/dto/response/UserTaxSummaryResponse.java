package com.fpt.producerworkbench.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.fpt.producerworkbench.common.TaxPeriodType;
import com.fpt.producerworkbench.common.TaxSummaryStatus;

/**
 * Response cho User Tax Summary
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserTaxSummaryResponse {
    
    private Long id;
    private Long userId;
    private String userFullName;
    private String userCccd;
    private String userTaxCode;
    
    private TaxPeriodType taxPeriodType;
    private Integer taxPeriodYear;
    private Integer taxPeriodMonth;
    private Integer taxPeriodQuarter;
    private LocalDate periodStartDate;
    private LocalDate periodEndDate;
    
    private BigDecimal totalGrossIncome;
    private BigDecimal totalTaxableIncome;
    private BigDecimal incomeFromMilestone;
    private BigDecimal incomeFromTermination;
    private BigDecimal incomeFromRefund;
    private BigDecimal incomeFromOther;
    
    private BigDecimal totalTaxWithheld;
    private BigDecimal totalTaxPaid;
    private BigDecimal totalTaxRefunded;
    private BigDecimal totalTaxDue;
    private BigDecimal effectiveTaxRate;
    
    private Integer totalPayoutCount;
    private Integer totalContractCount;
    private Integer totalWithdrawalCount;
    
    private TaxSummaryStatus status;
}


