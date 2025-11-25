package com.fpt.producerworkbench.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
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

    /** Nội dung điều khoản của cột mốc */
    @NotBlank
    @JsonProperty("description")
    String description;

    @JsonProperty("numofmoney")
    BigDecimal numOfMoney;

    @JsonProperty("numofedit")
    Integer numOfEdit;

    @JsonProperty("numofrefresh")
    Integer numOfRefresh;
}
