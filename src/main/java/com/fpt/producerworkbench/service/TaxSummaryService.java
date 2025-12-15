package com.fpt.producerworkbench.service;

import com.fpt.producerworkbench.common.TaxPeriodType;
import com.fpt.producerworkbench.entity.UserTaxSummary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Service xử lý tổng hợp thuế theo kỳ
 */
public interface TaxSummaryService {
    
    /**
     * Tính toán summary cho user trong tháng/quý/năm
     */
    UserTaxSummary calculateUserTaxSummary(
        Long userId,
        TaxPeriodType periodType,
        int year,
        Integer monthOrQuarter
    );
    
    /**
     * Báo cáo thuế theo người dùng
     */
    Page<UserTaxSummary> getUserTaxReport(
        Integer year,
        Integer month,
        Pageable pageable
    );
}

