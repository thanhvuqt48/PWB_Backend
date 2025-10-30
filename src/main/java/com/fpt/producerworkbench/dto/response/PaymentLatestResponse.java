package com.fpt.producerworkbench.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentLatestResponse {

    private String orderCode;
    private String status; // PENDING | SUCCESSFUL | FAILED
    private BigDecimal amount;

    private Long projectId;
    private Long contractId;

    private String paymentType; // FULL | MILESTONE
    private Long milestoneId; // nullable
    private Integer milestoneSequence; // nullable

    private Date createdAt;
    private Date updatedAt;
}


