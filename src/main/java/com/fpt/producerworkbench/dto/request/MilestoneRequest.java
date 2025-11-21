package com.fpt.producerworkbench.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDate;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class MilestoneRequest {

    @JsonProperty("title")
    @NotBlank(message = "Milestone title is required")
    private String title;

    @JsonProperty("description")
    private String description;

    @JsonProperty("amount")
    @NotBlank(message = "Milestone amount is required")
    private String amount;

    @JsonProperty("dueDate")
    @NotNull(message = "Milestone dueDate is required")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate dueDate;

    @JsonProperty("editCount")
    Integer editCount;

    @NotNull
    @JsonProperty("productCount")
    Integer productCount;
}
