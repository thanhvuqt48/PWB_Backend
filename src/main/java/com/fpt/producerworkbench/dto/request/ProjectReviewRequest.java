package com.fpt.producerworkbench.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectReviewRequest {

    @NotNull(message = "Số sao không được để trống")
    @Min(value = 1, message = "Tối thiểu 1 sao")
    @Max(value = 5, message = "Tối đa 5 sao")
    private Integer rating;

    private String comment;

    @Builder.Default
    private Boolean allowPublicPortfolio = false;
}