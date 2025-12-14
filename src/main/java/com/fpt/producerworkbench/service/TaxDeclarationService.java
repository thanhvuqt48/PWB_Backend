package com.fpt.producerworkbench.service;

import com.fpt.producerworkbench.common.TaxDeclarationStatus;
import com.fpt.producerworkbench.common.TaxPeriodType;
import com.fpt.producerworkbench.controller.AdminTaxController;
import com.fpt.producerworkbench.entity.TaxDeclaration;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Service xử lý tờ khai thuế
 */
public interface TaxDeclarationService {
    
    /**
     * Tạo tờ khai thuế cho kỳ
     */
    TaxDeclaration createTaxDeclaration(
        TaxPeriodType periodType,
        int year,
        Integer monthOrQuarter
    );
    
    /**
     * Lấy tờ khai theo ID
     */
    TaxDeclaration getById(Long id);
    
    /**
     * Danh sách tờ khai với filter
     */
    Page<TaxDeclaration> listDeclarations(
        Integer year,
        TaxDeclarationStatus status,
        Pageable pageable
    );
    
    /**
     * Nộp tờ khai
     */
    TaxDeclaration submit(Long id);
    
    /**
     * Export file (XML/PDF/Excel)
     */
    byte[] exportFile(Long id, String format);
    
    /**
     * Dashboard data
     */
    AdminTaxController.TaxDashboardData getDashboard(Integer year);
    
    /**
     * Monthly report
     */
    AdminTaxController.MonthlyTaxReportResponse getMonthlyReport(Integer year);
}


