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
    private Long addendumId; // nullable - chỉ có khi thanh toán phụ lục

    private String paymentType; // FULL | MILESTONE (chỉ có khi thanh toán contract)
    private Long milestoneId; // nullable (chỉ có khi thanh toán contract milestone)
    private Integer milestoneSequence; // nullable (chỉ có khi thanh toán contract milestone)

    private Date createdAt;
    private Date updatedAt;
}


