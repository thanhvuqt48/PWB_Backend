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
public class TaxOverviewResponse {
    private BigDecimal totalGross;
    private BigDecimal totalTax;
    private BigDecimal totalNet;
    private long totalPayoutCount;
    private long totalContractCount;
    private long totalProjectCount;
    private List<TaxSourceBreakdownResponse> sourceBreakdown;
    private List<TaxSeriesPointResponse> timeSeries;
}



