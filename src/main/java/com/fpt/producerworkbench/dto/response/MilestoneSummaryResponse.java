package com.fpt.producerworkbench.dto.response;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MilestoneSummaryResponse {

    private String description;
    private BigDecimal amount;
}
