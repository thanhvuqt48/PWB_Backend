package com.fpt.producerworkbench.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;

@Setter
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ContractAddendumMilestoneItemRequest {


    @JsonProperty("milestoneId")
    Long milestoneId;

    @JsonProperty("title")
    String title;

    @JsonProperty("description")
    String description;

    @JsonProperty("numOfMoney")
    BigDecimal numOfMoney;

    @JsonProperty("numOfEdit")
    Integer numOfEdit;

    @JsonProperty("numOfRefresh")
    Integer numOfRefresh;
}
