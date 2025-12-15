package com.fpt.producerworkbench.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminTaxOverviewResponse {
    private BigDecimal totalGross;
    private BigDecimal totalTaxWithheld;
    private BigDecimal totalTaxPaid;
    private BigDecimal totalTaxDue;
    private long totalPayoutCount;
    private long totalUserCount;
    private long totalProjectCount;
    private List<TaxSourceBreakdownResponse> sourceBreakdown;
    private List<AdminTaxSeriesPointResponse> timeSeries;
}


