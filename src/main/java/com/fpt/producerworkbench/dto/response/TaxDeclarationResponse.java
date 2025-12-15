package com.fpt.producerworkbench.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.fpt.producerworkbench.common.TaxDeclarationStatus;
import com.fpt.producerworkbench.common.TaxDeclarationType;
import com.fpt.producerworkbench.common.TaxPeriodType;

/**
 * Response cho Tax Declaration
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaxDeclarationResponse {
    
    private Long id;
    private String declarationCode;
    private TaxDeclarationType declarationType;
    private String taxFormVersion;
    
    private TaxPeriodType taxPeriodType;
    private Integer taxPeriodYear;
    private Integer taxPeriodMonth;
    private Integer taxPeriodQuarter;
    
    private String companyName;
    private String companyTaxCode;
    private String taxDepartment;
    
    private Integer totalEmployeeCount;
    private BigDecimal totalTaxableIncome;
    private BigDecimal totalTaxWithheld;
    private BigDecimal totalTaxPaid;
    private BigDecimal totalTaxDue;
    private BigDecimal totalTaxRefund;
    
    private String xmlFileUrl;
    private String pdfFileUrl;
    private String excelFileUrl;
    
    private TaxDeclarationStatus status;
    private LocalDateTime submittedAt;
    private String submittedBy;
    private LocalDateTime acceptedAt;
    private String acceptanceCode;
    
    private String notes;
    private String rejectionReason;
}


