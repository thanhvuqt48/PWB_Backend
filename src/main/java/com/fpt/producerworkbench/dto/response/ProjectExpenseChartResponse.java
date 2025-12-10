package com.fpt.producerworkbench.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.math.BigDecimal;
import java.util.Map;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectExpenseChartResponse {

    @JsonProperty("totalExpenseAmount")
    private BigDecimal totalExpenseAmount;

    @JsonProperty("totalMoneySplitAmount")
    private BigDecimal totalMoneySplitAmount;

    @JsonProperty("remainingAmount")
    private BigDecimal remainingAmount;

    @JsonProperty("remainingAfterTax")
    private BigDecimal remainingAfterTax;

    @JsonProperty("totalTax")
    private BigDecimal totalTax;

    @JsonProperty("contractTotalAmount")
    private BigDecimal contractTotalAmount;

    @JsonProperty("percentages")
    private Map<String, BigDecimal> percentages;
}

