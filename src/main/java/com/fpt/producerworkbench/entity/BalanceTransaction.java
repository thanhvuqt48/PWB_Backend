package com.fpt.producerworkbench.entity;

import com.fpt.producerworkbench.common.TransactionStatus;
import com.fpt.producerworkbench.common.TransactionType;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Giao dịch balance của user
 */
@Entity
@Table(name = "balance_transactions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BalanceTransaction extends AbstractEntity<Long> {
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contract_id")
    private Contract contract;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 50)
    private TransactionType type;
    
    @Column(name = "amount", precision = 15, scale = 2, nullable = false)
    private BigDecimal amount;
    
    @Column(name = "balance_before", precision = 15, scale = 2, nullable = false)
    private BigDecimal balanceBefore;
    
    @Column(name = "balance_after", precision = 15, scale = 2, nullable = false)
    private BigDecimal balanceAfter;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private TransactionStatus status; // PENDING, COMPLETED, FAILED
    
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;
    
    @Column(name = "reference_id", length = 100)
    private String referenceId; // TaxRecord.id hoặc reference khác
    
    @Column(name = "completed_at")
    private LocalDateTime completedAt;
}


