package com.fpt.producerworkbench.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserBankResponse {
    private Long id;
    private BankResponse bank;
    private String accountNumber;
    private String accountHolderName;
    private Boolean isVerified;
}

