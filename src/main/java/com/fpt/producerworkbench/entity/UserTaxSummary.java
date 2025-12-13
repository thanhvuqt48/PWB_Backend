package com.fpt.producerworkbench.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.fpt.producerworkbench.common.TaxPeriodType;
import com.fpt.producerworkbench.common.TaxSummaryStatus;

/**
 * Báo cáo thuế tổng hợp của user theo kỳ
 */
@Entity
@Table(name = "user_tax_summaries")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserTaxSummary extends AbstractEntity<Long> {
    
    // === NGƯỜI NỘP THUẾ ===
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @Column(name = "user_cccd")
    private String userCccd;
    
    @Column(name = "user_tax_code")
    private String userTaxCode;
    
    @Column(name = "user_full_name")
    private String userFullName;
    
    // === KỲ THUẾ ===
    @Enumerated(EnumType.STRING)
    @Column(name = "tax_period_type", nullable = false)
    private TaxPeriodType taxPeriodType;
    
    @Column(name = "tax_period_year", nullable = false)
    private Integer taxPeriodYear;
    
    @Column(name = "tax_period_month")
    private Integer taxPeriodMonth;
    
    @Column(name = "tax_period_quarter")
    private Integer taxPeriodQuarter;
    
    @Column(name = "period_start_date", nullable = false)
    private LocalDate periodStartDate;
    
    @Column(name = "period_end_date", nullable = false)
    private LocalDate periodEndDate;
    
    // === TỔNG HỢP THU NHẬP ===
    @Column(name = "total_gross_income", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal totalGrossIncome = BigDecimal.ZERO;
    
    @Column(name = "total_taxable_income", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal totalTaxableIncome = BigDecimal.ZERO;
    
    @Column(name = "total_non_taxable_income", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal totalNonTaxableIncome = BigDecimal.ZERO;
    
    // === PHÂN LOẠI THU NHẬP ===
    @Column(name = "income_from_milestone", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal incomeFromMilestone = BigDecimal.ZERO;
    
    @Column(name = "income_from_termination", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal incomeFromTermination = BigDecimal.ZERO;
    
    @Column(name = "income_from_refund", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal incomeFromRefund = BigDecimal.ZERO;
    
    @Column(name = "income_from_other", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal incomeFromOther = BigDecimal.ZERO;
    
    // === THUẾ ===
    @Column(name = "total_tax_withheld", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal totalTaxWithheld = BigDecimal.ZERO;
    
    @Column(name = "total_tax_paid", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal totalTaxPaid = BigDecimal.ZERO;
    
    @Column(name = "total_tax_refunded", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal totalTaxRefunded = BigDecimal.ZERO;
    
    @Column(name = "total_tax_due", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal totalTaxDue = BigDecimal.ZERO;
    
    @Column(name = "effective_tax_rate", precision = 5, scale = 4)
    private BigDecimal effectiveTaxRate;
    
    // === SỐ LƯỢNG GIAO DỊCH ===
    @Column(name = "total_payout_count")
    @Builder.Default
    private Integer totalPayoutCount = 0;
    
    @Column(name = "total_contract_count")
    @Builder.Default
    private Integer totalContractCount = 0;
    
    @Column(name = "total_withdrawal_count")
    @Builder.Default
    private Integer totalWithdrawalCount = 0;
    
    // === TRẠNG THÁI ===
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private TaxSummaryStatus status;
    
    @Column(name = "finalized_at")
    private LocalDateTime finalizedAt;
    
    @Column(name = "declared_at")
    private LocalDateTime declaredAt;
    
    @Column(name = "paid_at")
    private LocalDateTime paidAt;
    
    // === GHI CHÚ ===
    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;
}


