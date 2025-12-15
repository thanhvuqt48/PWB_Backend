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
public class TaxSeriesPointResponse {
    private String periodLabel; // ví dụ: 2025-01 hoặc 2025
    private BigDecimal gross;
    private BigDecimal tax;
    private BigDecimal net;
}



