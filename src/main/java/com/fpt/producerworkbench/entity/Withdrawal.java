package com.fpt.producerworkbench.entity;

import com.fpt.producerworkbench.common.WithdrawalStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "withdrawals")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Withdrawal extends AbstractEntity<Long> {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(precision = 15, scale = 2, nullable = false)
    private BigDecimal amount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bank_id", nullable = false)
    private Bank bank;

    @Column(name = "account_number", length = 50, nullable = false)
    private String accountNumber;

    @Column(name = "account_holder_name", length = 255, nullable = false)
    private String accountHolderName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private WithdrawalStatus status = WithdrawalStatus.PENDING;

    @Column(name = "withdrawal_code", unique = true)
    private String withdrawalCode;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @Column(name = "qr_data_url", columnDefinition = "LONGTEXT")
    private String qrDataURL;
}

