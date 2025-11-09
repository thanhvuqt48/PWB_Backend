package com.fpt.producerworkbench.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateMoneySplitRequest {

    @JsonProperty("amount")
    @NotBlank(message = "Amount is required")
    private String amount;

    @JsonProperty("note")
    private String note;
}


