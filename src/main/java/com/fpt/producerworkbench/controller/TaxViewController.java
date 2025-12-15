package com.fpt.producerworkbench.controller;

import com.fpt.producerworkbench.common.PayoutSource;
import com.fpt.producerworkbench.dto.response.ApiResponse;
import com.fpt.producerworkbench.dto.response.PageResponse;
import com.fpt.producerworkbench.dto.response.TaxOverviewResponse;
import com.fpt.producerworkbench.dto.response.TaxTransactionResponse;
import com.fpt.producerworkbench.service.TaxViewService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * API Thuế & Thu nhập cho người dùng (dashboard/statement).
 */
@RestController
@RequestMapping("/api/v1/taxes")
@RequiredArgsConstructor
public class TaxViewController {

    private final TaxViewService taxViewService;

    /**
     * Tổng quan gross/tax/net và chuỗi thời gian.
     */
    @GetMapping("/overview")
    public ResponseEntity<ApiResponse<TaxOverviewResponse>> getOverview(
            @RequestParam(value = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(value = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(value = "groupBy", defaultValue = "MONTH") String groupBy
    ) {
        LocalDate now = LocalDate.now();
        LocalDate from = fromDate != null ? fromDate : now.minusMonths(12).withDayOfMonth(1);
        LocalDate to = toDate != null ? toDate : now;

        TaxOverviewResponse response = taxViewService.getOverview(from, to, groupBy);
        return ResponseEntity.ok(ApiResponse.<TaxOverviewResponse>builder()
                .result(response)
                .build());
    }

    /**
     * Danh sách giao dịch payout có khấu trừ thuế (COMPLETED).
     */
    @GetMapping("/transactions")
    public ResponseEntity<ApiResponse<PageResponse<TaxTransactionResponse>>> getTransactions(
            @RequestParam(value = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(value = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(value = "source", required = false) PayoutSource source,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size
    ) {
        LocalDate now = LocalDate.now();
        LocalDate from = fromDate != null ? fromDate : now.minusMonths(12).withDayOfMonth(1);
        LocalDate to = toDate != null ? toDate : now;

        Pageable pageable = PageRequest.of(page, size);
        Page<TaxTransactionResponse> result = taxViewService.getTransactions(from, to, source, pageable);
        PageResponse<TaxTransactionResponse> pageResponse = PageResponse.fromPage(result);

        return ResponseEntity.ok(ApiResponse.<PageResponse<TaxTransactionResponse>>builder()
                .result(pageResponse)
                .build());
    }

    /**
     * Xuất giao dịch payout ra file (CSV).
     */
    @GetMapping("/transactions/export")
    public ResponseEntity<byte[]> exportTransactions(
            @RequestParam(value = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(value = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(value = "source", required = false) PayoutSource source,
            @RequestParam(value = "format", defaultValue = "CSV") String format
    ) {
        LocalDate now = LocalDate.now();
        LocalDate from = fromDate != null ? fromDate : now.minusMonths(12).withDayOfMonth(1);
        LocalDate to = toDate != null ? toDate : now;

        var file = taxViewService.exportTransactions(from, to, source, format);
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"" + file.filename + "\"")
                .header("Content-Type", file.contentType)
                .body(file.bytes);
    }
}


