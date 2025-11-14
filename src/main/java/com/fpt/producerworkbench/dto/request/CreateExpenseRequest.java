package com.fpt.producerworkbench.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateExpenseRequest {

    @JsonProperty("name")
    @NotBlank(message = "Expense name is required")
    private String name;

    @JsonProperty("description")
    private String description;

    @JsonProperty("amount")
    @NotBlank(message = "Amount is required")
    private String amount;
}

