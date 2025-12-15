package com.fpt.producerworkbench.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Chi tiết từng người trong tờ khai thuế
 */
@Entity
@Table(name = "tax_declaration_details")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaxDeclarationDetail extends AbstractEntity<Long> {
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tax_declaration_id", nullable = false)
    private TaxDeclaration taxDeclaration;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @Column(name = "user_tax_summary_id")
    private Long userTaxSummaryId;
    
    // === THÔNG TIN CÁ NHÂN ===
    @Column(name = "sequence_number")
    private Integer sequenceNumber; // STT trong tờ khai
    
    @Column(name = "full_name", nullable = false)
    private String fullName;
    
    @Column(name = "cccd_number", nullable = false)
    private String cccdNumber;
    
    @Column(name = "tax_code")
    private String taxCode;
    
    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;
    
    @Column(name = "address")
    private String address;
    
    // === THU NHẬP VÀ THUẾ ===
    @Column(name = "taxable_income", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal taxableIncome = BigDecimal.ZERO;
    
    @Column(name = "tax_withheld", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal taxWithheld = BigDecimal.ZERO;
    
    @Column(name = "tax_paid", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal taxPaid = BigDecimal.ZERO;
    
    @Column(name = "tax_due", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal taxDue = BigDecimal.ZERO;
    
    @Column(name = "tax_refund", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal taxRefund = BigDecimal.ZERO;
    
    // === GHI CHÚ ===
    @Column(name = "notes")
    private String notes;
}


