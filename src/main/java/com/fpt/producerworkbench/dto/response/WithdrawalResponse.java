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
public class WithdrawalResponse {
    private Long id;
    private String withdrawalCode;
    private BigDecimal amount;
    private BankResponse bank;
    private String accountNumber;
    private String accountHolderName;
    private String status;
    private String rejectionReason;
    private Date createdAt;
    private BigDecimal remainingBalance;
    private String qrDataURL;
}

