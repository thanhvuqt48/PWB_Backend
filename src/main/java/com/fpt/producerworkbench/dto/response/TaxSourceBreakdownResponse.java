package com.fpt.producerworkbench.dto.response;

import com.fpt.producerworkbench.common.PayoutSource;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaxSourceBreakdownResponse {
    private PayoutSource source;
    private BigDecimal gross;
    private BigDecimal tax;
    private BigDecimal net;
    private long payoutCount;
}



