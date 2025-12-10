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
public class ProjectMoneySplitDetailResponse {

    @JsonProperty("milestoneId")
    private Long milestoneId;

    @JsonProperty("milestoneTitle")
    private String milestoneTitle;

    @JsonProperty("milestoneSequence")
    private Integer milestoneSequence;

    @JsonProperty("milestoneTotalAmount")
    private BigDecimal milestoneTotalAmount;

    @JsonProperty("totalMoneySplitAmount")
    private BigDecimal totalMoneySplitAmount;

    @JsonProperty("moneySplits")
    private List<MilestoneMoneySplitResponse> moneySplits;
}

