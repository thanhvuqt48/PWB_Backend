package com.fpt.producerworkbench.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MilestoneMoneySplitDetailResponse {

    @JsonProperty("moneySplits")
    private List<MilestoneMoneySplitResponse> moneySplits;

    @JsonProperty("expenses")
    private List<MilestoneExpenseResponse> expenses;

    @JsonProperty("totalSplitAmount")
    private BigDecimal totalSplitAmount;

    @JsonProperty("totalExpenseAmount")
    private BigDecimal totalExpenseAmount;

    @JsonProperty("totalAllocated")
    private BigDecimal totalAllocated;

    @JsonProperty("milestoneAmount")
    private BigDecimal milestoneAmount;

    @JsonProperty("remainingAmount")
    private BigDecimal remainingAmount;
}


