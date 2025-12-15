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
public class AdminTaxSeriesPointResponse {
    private String periodLabel; // yyyy-MM hoặc yyyy
    private BigDecimal gross;
    private BigDecimal taxWithheld;
    private BigDecimal taxPaid;
    private BigDecimal taxDue;
}


