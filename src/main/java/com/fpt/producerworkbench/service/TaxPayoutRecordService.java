package com.fpt.producerworkbench.service;

import com.fpt.producerworkbench.entity.TaxPayoutRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;

/**
 * Service xử lý Tax Payout Records
 */
public interface TaxPayoutRecordService {
    
    /**
     * Danh sách payout records với filter
     */
    Page<TaxPayoutRecord> listRecords(
        Long userId,
        LocalDate fromDate,
        LocalDate toDate,
        Boolean isDeclared,
        Pageable pageable
    );
    
    /**
     * Đánh dấu các payouts đã kê khai
     */
    void markAsDeclared(List<Long> payoutIds, Long declarationId);
}


