package com.fpt.producerworkbench.controller;

import com.fpt.producerworkbench.common.PayoutSource;
import com.fpt.producerworkbench.dto.response.ApiResponse;
import com.fpt.producerworkbench.dto.response.PageResponse;
import com.fpt.producerworkbench.dto.response.AdminTaxOverviewResponse;
import com.fpt.producerworkbench.dto.response.AdminTaxPayoutResponse;
import com.fpt.producerworkbench.service.AdminTaxService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/admin/taxes")
@RequiredArgsConstructor
public class AdminTaxViewController {

    private final AdminTaxService adminTaxService;

    @GetMapping("/overview")
    public ResponseEntity<ApiResponse<AdminTaxOverviewResponse>> getOverview(
            @RequestParam(value = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(value = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(value = "groupBy", defaultValue = "MONTH") String groupBy
    ) {
        LocalDate now = LocalDate.now();
        LocalDate from = fromDate != null ? fromDate : now.minusMonths(12).withDayOfMonth(1);
        LocalDate to = toDate != null ? toDate : now;

        AdminTaxOverviewResponse result = adminTaxService.getOverview(from, to, groupBy);
        return ResponseEntity.ok(ApiResponse.<AdminTaxOverviewResponse>builder()
                .result(result)
                .build());
    }

    @GetMapping("/payouts")
    public ResponseEntity<ApiResponse<PageResponse<AdminTaxPayoutResponse>>> getPayouts(
            @RequestParam(value = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(value = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(value = "month", required = false) Integer month,
            @RequestParam(value = "year", required = false) Integer year,
            @RequestParam(value = "quarter", required = false) Integer quarter,
            @RequestParam(value = "userId", required = false) Long userId,
            @RequestParam(value = "projectId", required = false) Long projectId,
            @RequestParam(value = "contractId", required = false) Long contractId,
            @RequestParam(value = "source", required = false) PayoutSource source,
            @RequestParam(value = "declared", required = false) Boolean declared,
            @RequestParam(value = "paid", required = false) Boolean paid,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size
    ) {
        LocalDate now = LocalDate.now();
        LocalDate from = fromDate != null ? fromDate : now.minusMonths(12).withDayOfMonth(1);
        LocalDate to = toDate != null ? toDate : now;

        Pageable pageable = PageRequest.of(page, size);
        Page<AdminTaxPayoutResponse> result = adminTaxService.getPayouts(
                from, to, month, year, quarter, userId, projectId, contractId, source, declared, paid, pageable
        );
        return ResponseEntity.ok(ApiResponse.<PageResponse<AdminTaxPayoutResponse>>builder()
                .result(PageResponse.fromPage(result))
                .build());
    }

    @GetMapping("/payouts/export")
    public ResponseEntity<byte[]> exportPayouts(
            @RequestParam(value = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(value = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(value = "month", required = false) Integer month,
            @RequestParam(value = "year", required = false) Integer year,
            @RequestParam(value = "quarter", required = false) Integer quarter,
            @RequestParam(value = "userId", required = false) Long userId,
            @RequestParam(value = "projectId", required = false) Long projectId,
            @RequestParam(value = "contractId", required = false) Long contractId,
            @RequestParam(value = "source", required = false) PayoutSource source,
            @RequestParam(value = "declared", required = false) Boolean declared,
            @RequestParam(value = "paid", required = false) Boolean paid,
            @RequestParam(value = "format", defaultValue = "CSV") String format
    ) {
        LocalDate now = LocalDate.now();
        LocalDate from = fromDate != null ? fromDate : now.minusMonths(12).withDayOfMonth(1);
        LocalDate to = toDate != null ? toDate : now;

        var file = adminTaxService.exportPayouts(
                from, to, month, year, quarter, userId, projectId, contractId, source, declared, paid, format
        );
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"" + file.filename + "\"")
                .header("Content-Type", file.contentType)
                .body(file.bytes);
    }

    @PostMapping("/payouts/mark")
    public ResponseEntity<ApiResponse<Void>> markPayouts(
            @RequestParam(value = "declared", required = false) Boolean declared,
            @RequestParam(value = "paid", required = false) Boolean paid,
            @RequestParam(value = "ids") java.util.List<Long> ids
    ) {
        adminTaxService.markPayouts(ids, declared, paid);
        return ResponseEntity.ok(ApiResponse.<Void>builder().build());
    }
}

