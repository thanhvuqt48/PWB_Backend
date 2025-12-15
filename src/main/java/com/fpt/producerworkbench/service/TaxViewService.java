package com.fpt.producerworkbench.service;

import com.fpt.producerworkbench.common.PayoutSource;
import com.fpt.producerworkbench.dto.response.TaxOverviewResponse;
import com.fpt.producerworkbench.dto.response.TaxTransactionResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;

/**
 * Service cung cấp dữ liệu dashboard Thuế & Thu nhập cho người dùng.
 */
public interface TaxViewService {

    /**
     * Tổng quan gross/tax/net + chuỗi thời gian.
     */
    TaxOverviewResponse getOverview(
            LocalDate fromDate,
            LocalDate toDate,
            String groupBy
    );

    /**
     * Danh sách giao dịch chịu thuế (payout) của user.
     */
    Page<TaxTransactionResponse> getTransactions(
            LocalDate fromDate,
            LocalDate toDate,
            PayoutSource source,
            Pageable pageable
    );

    /**
     * Xuất giao dịch payout ra file (CSV tạm thời).
     */
    ExportedFile exportTransactions(
            LocalDate fromDate,
            LocalDate toDate,
            PayoutSource source,
            String format
    );

    /**
     * Dữ liệu cho render template PDF.
     */
    @lombok.Builder
    @lombok.Value
    class TransactionExportRow {
        LocalDate payoutDate;
        PayoutSource source;
        String projectTitle;
        java.math.BigDecimal grossAmount;
        java.math.BigDecimal taxAmount;
        java.math.BigDecimal netAmount;
        com.fpt.producerworkbench.common.PayoutStatus status;
        Integer taxPeriodYear;
        Integer taxPeriodMonth;
    }

    /**
     * Thông tin file xuất.
     */
    class ExportedFile {
        public final byte[] bytes;
        public final String filename;
        public final String contentType;

        public ExportedFile(byte[] bytes, String filename, String contentType) {
            this.bytes = bytes;
            this.filename = filename;
            this.contentType = contentType;
        }
    }
}


