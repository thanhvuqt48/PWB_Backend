package com.fpt.producerworkbench.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.fpt.producerworkbench.common.TaxDeclarationStatus;
import com.fpt.producerworkbench.common.TaxDeclarationType;
import com.fpt.producerworkbench.common.TaxPeriodType;

/**
 * Tờ khai thuế 05-KK-TNCN
 */
@Entity
@Table(name = "tax_declarations")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaxDeclaration extends AbstractEntity<Long> {
    
    // === THÔNG TIN CHUNG ===
    @Column(name = "declaration_code", unique = true, nullable = false)
    private String declarationCode;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "declaration_type", nullable = false)
    private TaxDeclarationType declarationType;
    
    @Column(name = "tax_form_version")
    private String taxFormVersion;
    
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
    
    // === ĐƠN VỊ NỘP THUẾ (HỆ THỐNG) ===
    @Column(name = "company_name")
    private String companyName;
    
    @Column(name = "company_tax_code")
    private String companyTaxCode;
    
    @Column(name = "company_address")
    private String companyAddress;
    
    @Column(name = "company_phone")
    private String companyPhone;
    
    @Column(name = "company_email")
    private String companyEmail;
    
    @Column(name = "legal_representative")
    private String legalRepresentative;
    
    // === CHI CỤC THUẾ ===
    @Column(name = "tax_department")
    private String taxDepartment;
    
    @Column(name = "tax_department_code")
    private String taxDepartmentCode;
    
    // === NỘI DUNG TỜ KHAI ===
    @Column(name = "total_employee_count")
    @Builder.Default
    private Integer totalEmployeeCount = 0;
    
    @Column(name = "total_taxable_income", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal totalTaxableIncome = BigDecimal.ZERO;
    
    @Column(name = "total_tax_withheld", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal totalTaxWithheld = BigDecimal.ZERO;
    
    @Column(name = "total_tax_paid", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal totalTaxPaid = BigDecimal.ZERO;
    
    @Column(name = "total_tax_due", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal totalTaxDue = BigDecimal.ZERO;
    
    @Column(name = "total_tax_refund", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal totalTaxRefund = BigDecimal.ZERO;
    
    // === FILE ĐÍNH KÈM ===
    @Column(name = "xml_file_url", length = 1000)
    private String xmlFileUrl;
    
    @Column(name = "pdf_file_url", length = 1000)
    private String pdfFileUrl;
    
    @Column(name = "excel_file_url", length = 1000)
    private String excelFileUrl;
    
    // === TRẠNG THÁI ===
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private TaxDeclarationStatus status;
    
    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;
    
    @Column(name = "submitted_by")
    private String submittedBy;
    
    @Column(name = "accepted_at")
    private LocalDateTime acceptedAt;
    
    @Column(name = "acceptance_code")
    private String acceptanceCode;
    
    // === GHI CHÚ ===
    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;
    
    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;
}


