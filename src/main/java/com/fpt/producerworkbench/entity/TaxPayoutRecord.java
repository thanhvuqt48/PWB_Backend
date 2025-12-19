package com.fpt.producerworkbench.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.fpt.producerworkbench.common.PayoutMethod;
import com.fpt.producerworkbench.common.PayoutSource;
import com.fpt.producerworkbench.common.PayoutStatus;

/**
 * Ghi nhận từng lần giải ngân (payout) cho mục đích kê khai thuế
 * Mỗi lần user nhận tiền vào balance phải được ghi nhận
 */
@Entity
@Table(name = "tax_payout_records")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaxPayoutRecord extends AbstractEntity<Long> {
    
    // === NGƯỜI NHẬN ===
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @Column(name = "user_cccd")
    private String userCccd; // Denormalize để báo cáo nhanh
    
    @Column(name = "user_tax_code")
    private String userTaxCode;
    
    @Column(name = "user_full_name")
    private String userFullName;
    
    // === NGUỒN TIỀN ===
    @Enumerated(EnumType.STRING)
    @Column(name = "payout_source", nullable = false)
    private PayoutSource payoutSource;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contract_id")
    private Contract contract;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "milestone_id")
    private Milestone milestone;
    
    @Column(name = "termination_id")
    private Long terminationId;
    
    // === SỐ TIỀN ===
    @Column(name = "gross_amount", precision = 15, scale = 2, nullable = false)
    private BigDecimal grossAmount; // Số tiền trước thuế
    
    @Column(name = "tax_amount", precision = 15, scale = 2, nullable = false)
    private BigDecimal taxAmount; // Thuế đã khấu trừ (7%)
    
    @Column(name = "net_amount", precision = 15, scale = 2, nullable = false)
    private BigDecimal netAmount; // Số tiền sau thuế (thực nhận)
    
    @Column(name = "tax_rate", precision = 5, scale = 2)
    private BigDecimal taxRate; // Thuế suất (0.07 = 7%)
    
    @Column(name = "tax_category")
    private String taxCategory; // Loại thu nhập: "Tiền công, tiền lương"
    
    // === THỜI GIAN ===
    @Column(name = "payout_date", nullable = false)
    private LocalDate payoutDate;
    
    @Column(name = "tax_period_month")
    private Integer taxPeriodMonth; // Tháng kỳ thuế (1-12)
    
    @Column(name = "tax_period_year")
    private Integer taxPeriodYear;
    
    @Column(name = "tax_period_quarter")
    private Integer taxPeriodQuarter; // Quý (1-4)
    
    // === CHI TIẾT ===
    @Enumerated(EnumType.STRING)
    @Column(name = "payout_method")
    private PayoutMethod payoutMethod;
    
    @Column(name = "withdrawal_id")
    private Long withdrawalId;
    
    @Column(name = "balance_transaction_id")
    private Long balanceTransactionId;
    
    @Column(name = "reference_code")
    private String referenceCode;
    
    // === TRẠNG THÁI ===
    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private PayoutStatus status;
    
    @Column(name = "completed_at")
    private LocalDateTime completedAt;
    
    // === KÊ KHAI THUẾ ===
    @Column(name = "is_tax_declared")
    @Builder.Default
    private Boolean isTaxDeclared = false;
    
    @Column(name = "tax_declaration_date")
    private LocalDate taxDeclarationDate;
    
    @Column(name = "tax_declaration_id")
    private Long taxDeclarationId;
    
    @Column(name = "tax_paid")
    @Builder.Default
    private Boolean taxPaid = false;
    
    @Column(name = "tax_payment_date")
    private LocalDate taxPaymentDate;
    
    // === GHI CHÚ ===
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;
    
    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;
}


