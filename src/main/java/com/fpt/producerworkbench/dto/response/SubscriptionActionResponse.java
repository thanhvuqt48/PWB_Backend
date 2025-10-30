package com.fpt.producerworkbench.dto.response;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubscriptionActionResponse {
    private String paymentUrl;
    private String orderCode;
    private BigDecimal amount;
    private String status;
}


