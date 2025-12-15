package com.fpt.producerworkbench.dto.response;

import com.fpt.producerworkbench.common.PayoutSource;
import com.fpt.producerworkbench.common.PayoutStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaxTransactionResponse {
    private Long id;
    private LocalDate payoutDate;
    private PayoutSource payoutSource;
    private PayoutStatus status;
    private String referenceCode;

    private BigDecimal grossAmount;
    private BigDecimal taxAmount;
    private BigDecimal netAmount;

    private Long projectId;
    private String projectTitle;
    private Long contractId;
    private Long milestoneId;
    private String milestoneTitle;

    private Integer taxPeriodMonth;
    private Integer taxPeriodYear;
    private Integer taxPeriodQuarter;
}



