package com.fpt.producerworkbench.entity;

import com.fpt.producerworkbench.common.TransactionStatus;
import com.fpt.producerworkbench.common.TransactionType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "transactions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Transaction extends AbstractEntity<Long>{

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(precision = 15, scale = 2, nullable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionStatus status;

    @Column(name = "transaction_code", unique = true)
    private String transactionCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "related_contract_id")
    private Contract relatedContract;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "related_milestone_id")
    private Milestone relatedMilestone;

}