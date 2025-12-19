package com.fpt.producerworkbench.service.impl;

import com.fpt.producerworkbench.common.TaxDeclarationStatus;
import com.fpt.producerworkbench.common.TaxPeriodType;
import com.fpt.producerworkbench.controller.AdminTaxController;
import com.fpt.producerworkbench.entity.TaxDeclaration;
import com.fpt.producerworkbench.exception.AppException;
import com.fpt.producerworkbench.exception.ErrorCode;
import com.fpt.producerworkbench.repository.TaxDeclarationRepository;
import com.fpt.producerworkbench.service.TaxDeclarationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Stub implementation of TaxDeclarationService
 * TODO: Implement full logic for tax declaration generation and submission
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TaxDeclarationServiceImpl implements TaxDeclarationService {
    
    private final TaxDeclarationRepository taxDeclarationRepository;
    
    @Override
    @Transactional
    public TaxDeclaration createTaxDeclaration(
            TaxPeriodType periodType,
            int year,
            Integer monthOrQuarter
    ) {
        log.info("Creating tax declaration for period: {} {}/{}",
                periodType, monthOrQuarter, year);
        
        // TODO: Implement full logic
        // 1. Tạo summary cho tất cả users
        // 2. Tổng hợp dữ liệu
        // 3. Tạo TaxDeclaration và TaxDeclarationDetails
        // 4. Export XML/PDF/Excel
        
        String declarationCode = generateDeclarationCode(periodType, year, monthOrQuarter);
        
        TaxDeclaration declaration = TaxDeclaration.builder()
                .declarationCode(declarationCode)
                .declarationType(com.fpt.producerworkbench.common.TaxDeclarationType.FORM_05_KK_TNCN)
                .taxFormVersion("2023")
                .taxPeriodType(periodType)
                .taxPeriodYear(year)
                .taxPeriodMonth(periodType == TaxPeriodType.MONTHLY ? monthOrQuarter : null)
                .taxPeriodQuarter(periodType == TaxPeriodType.QUARTERLY ? monthOrQuarter : null)
                .companyName("Producer Workbench JSC")
                .companyTaxCode("0123456789")
                .taxDepartment("Chi cục Thuế Quận 1")
                .status(TaxDeclarationStatus.DRAFT)
                .build();
        
        return taxDeclarationRepository.save(declaration);
    }
    
    @Override
    @Transactional(readOnly = true)
    public TaxDeclaration getById(Long id) {
        return taxDeclarationRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.TAX_DECLARATION_NOT_FOUND));
    }
    
    @Override
    @Transactional(readOnly = true)
    public Page<TaxDeclaration> listDeclarations(
            Integer year,
            TaxDeclarationStatus status,
            Pageable pageable
    ) {
        return taxDeclarationRepository.findByFilters(year, status, pageable);
    }
    
    @Override
    @Transactional
    public TaxDeclaration submit(Long id) {
        log.info("Submitting tax declaration: {}", id);
        
        TaxDeclaration declaration = getById(id);
        
        if (declaration.getStatus() == TaxDeclarationStatus.SUBMITTED ||
            declaration.getStatus() == TaxDeclarationStatus.ACCEPTED) {
            throw new AppException(ErrorCode.TAX_DECLARATION_ALREADY_SUBMITTED);
        }
        
        // TODO: Implement submission logic
        // 1. Validate data
        // 2. Generate final XML
        // 3. Submit to tax authority API (if available)
        
        declaration.setStatus(TaxDeclarationStatus.SUBMITTED);
        declaration.setSubmittedAt(LocalDateTime.now());
        declaration.setSubmittedBy("ADMIN"); // TODO: Get current admin user
        
        return taxDeclarationRepository.save(declaration);
    }
    
    @Override
    public byte[] exportFile(Long id, String format) {
        log.info("Exporting tax declaration {} in format: {}", id, format);
        
        TaxDeclaration declaration = getById(id);
        
        // TODO: Implement real export logic
        // For now, return dummy data
        String content = "Tax Declaration Export - " + format.toUpperCase() + "\n" +
                "ID: " + declaration.getId() + "\n" +
                "Code: " + declaration.getDeclarationCode() + "\n" +
                "Period: " + declaration.getTaxPeriodType() + " " +
                declaration.getTaxPeriodYear();
        
        return content.getBytes();
    }
    
    @Override
    public AdminTaxController.TaxDashboardData getDashboard(Integer year) {
        log.info("Getting tax dashboard for year: {}", year);
        
        // TODO: Implement real dashboard logic
        return new AdminTaxController.TaxDashboardData();
    }
    
    @Override
    public AdminTaxController.MonthlyTaxReportResponse getMonthlyReport(Integer year) {
        log.info("Getting monthly tax report for year: {}", year);
        
        // TODO: Implement real monthly report logic
        return AdminTaxController.MonthlyTaxReportResponse.builder()
                .year(year)
                .months(java.util.List.of())
                .build();
    }
    
    private String generateDeclarationCode(TaxPeriodType periodType, int year, Integer monthOrQuarter) {
        String prefix = switch (periodType) {
            case MONTHLY -> "TD-M";
            case QUARTERLY -> "TD-Q";
            case YEARLY -> "TD-Y";
        };
        
        String period = monthOrQuarter != null ? 
                String.format("%02d", monthOrQuarter) : "00";
        
        return String.format("%s-%s-%d-%d", prefix, period, year, System.currentTimeMillis() % 10000);
    }
}


