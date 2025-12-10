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
public class ProjectExpenseDetailResponse {

    @JsonProperty("milestoneId")
    private Long milestoneId;

    @JsonProperty("milestoneTitle")
    private String milestoneTitle;

    @JsonProperty("milestoneSequence")
    private Integer milestoneSequence;

    @JsonProperty("milestoneTotalAmount")
    private BigDecimal milestoneTotalAmount;

    @JsonProperty("totalExpenseAmount")
    private BigDecimal totalExpenseAmount;

    @JsonProperty("expenses")
    private List<MilestoneExpenseResponse> expenses;
}

