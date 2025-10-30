package com.fpt.producerworkbench.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentStatusResponse {

    private String orderCode;
    private String status; // PENDING | SUCCESSFUL | FAILED
    private BigDecimal amount;
    private Long projectId;
    private Long contractId;
}


