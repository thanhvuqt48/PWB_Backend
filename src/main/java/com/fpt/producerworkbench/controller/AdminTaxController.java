package com.fpt.producerworkbench.controller;

import com.fpt.producerworkbench.common.TaxDeclarationStatus;
import com.fpt.producerworkbench.dto.response.TaxDeclarationResponse;
import com.fpt.producerworkbench.dto.response.TaxPayoutRecordResponse;
import com.fpt.producerworkbench.dto.response.UserTaxSummaryResponse;
import com.fpt.producerworkbench.entity.TaxDeclaration;
import com.fpt.producerworkbench.entity.TaxPayoutRecord;
import com.fpt.producerworkbench.entity.UserTaxSummary;
import com.fpt.producerworkbench.service.TaxDeclarationService;
import com.fpt.producerworkbench.service.TaxPayoutRecordService;
import com.fpt.producerworkbench.service.TaxSummaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/**
 * Admin APIs for tax management
 */
@RestController
@RequestMapping("/api/v1/admin/tax")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminTaxController {
    
    private final TaxDeclarationService taxDeclarationService;
    private final TaxPayoutRecordService taxPayoutRecordService;
    private final TaxSummaryService taxSummaryService;
    
    // ===== TAX DECLARATIONS =====
    
    /**
     * Lấy danh sách tờ khai thuế
     */
    @GetMapping("/declarations")
    public ResponseEntity<Page<TaxDeclarationResponse>> listTaxDeclarations(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) TaxDeclarationStatus status,
            Pageable pageable
    ) {
        Page<TaxDeclaration> declarations = taxDeclarationService
                .listDeclarations(year, status, pageable);
        Page<TaxDeclarationResponse> response = declarations.map(this::toDeclarationResponse);
        return ResponseEntity.ok(response);
    }
    
    /**
     * Tạo tờ khai thuế cho kỳ
     */
    @PostMapping("/declarations")
    public ResponseEntity<TaxDeclarationResponse> createTaxDeclaration(
            @RequestBody TaxDeclarationRequest request
    ) {
        TaxDeclaration declaration = taxDeclarationService.createTaxDeclaration(
                request.getPeriodType(),
                request.getYear(),
                request.getMonthOrQuarter()
        );
        return ResponseEntity.ok(toDeclarationResponse(declaration));
    }
    
    /**
     * Lấy chi tiết tờ khai thuế
     */
    @GetMapping("/declarations/{id}")
    public ResponseEntity<TaxDeclarationResponse> getTaxDeclaration(
            @PathVariable Long id
    ) {
        TaxDeclaration declaration = taxDeclarationService.getById(id);
        return ResponseEntity.ok(toDeclarationResponse(declaration));
    }
    
    /**
     * Nộp tờ khai thuế
     */
    @PostMapping("/declarations/{id}/submit")
    public ResponseEntity<TaxDeclarationResponse> submitTaxDeclaration(
            @PathVariable Long id
    ) {
        TaxDeclaration declaration = taxDeclarationService.submit(id);
        return ResponseEntity.ok(toDeclarationResponse(declaration));
    }
    
    /**
     * Download tờ khai thuế (XML/PDF/Excel)
     */
    @GetMapping("/declarations/{id}/download")
    public ResponseEntity<Resource> downloadTaxDeclaration(
            @PathVariable Long id,
            @RequestParam(defaultValue = "pdf") String format
    ) {
        byte[] content = taxDeclarationService.exportFile(id, format);
        String filename = "tax-declaration-" + id + "." + format;
        
        MediaType mediaType = switch (format.toLowerCase()) {
            case "xml" -> MediaType.APPLICATION_XML;
            case "excel", "xlsx" -> MediaType.parseMediaType(
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            default -> MediaType.APPLICATION_PDF;
        };
        
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(mediaType)
                .body(new ByteArrayResource(content));
    }
    
    // ===== TAX PAYOUT RECORDS =====
    
    /**
     * Danh sách payout records (giao dịch giải ngân)
     */
    @GetMapping("/payouts")
    public ResponseEntity<Page<TaxPayoutRecordResponse>> listPayoutRecords(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) LocalDate fromDate,
            @RequestParam(required = false) LocalDate toDate,
            @RequestParam(required = false) Boolean isDeclared,
            Pageable pageable
    ) {
        Page<TaxPayoutRecord> records = taxPayoutRecordService
                .listRecords(userId, fromDate, toDate, isDeclared, pageable);
        Page<TaxPayoutRecordResponse> response = records.map(this::toPayoutRecordResponse);
        return ResponseEntity.ok(response);
    }
    
    /**
     * Đánh dấu các payout đã kê khai
     */
    @PostMapping("/payouts/mark-declared")
    public ResponseEntity<Void> markPayoutsAsDeclared(
            @RequestBody MarkDeclaredRequest request
    ) {
        taxPayoutRecordService.markAsDeclared(
                request.getPayoutIds(),
                request.getDeclarationId()
        );
        return ResponseEntity.ok().build();
    }
    
    // ===== TAX SUMMARIES =====
    
    /**
     * Báo cáo thuế theo người dùng
     */
    @GetMapping("/summaries")
    public ResponseEntity<Page<UserTaxSummaryResponse>> getUserTaxSummaries(
            @RequestParam Integer year,
            @RequestParam(required = false) Integer month,
            Pageable pageable
    ) {
        Page<UserTaxSummary> summaries = taxSummaryService
                .getUserTaxReport(year, month, pageable);
        Page<UserTaxSummaryResponse> response = summaries.map(this::toTaxSummaryResponse);
        return ResponseEntity.ok(response);
    }
    
    /**
     * Dashboard thống kê thuế
     */
    @GetMapping("/dashboard")
    public ResponseEntity<TaxDashboardResponse> getTaxDashboard(
            @RequestParam Integer year
    ) {
        TaxDashboardData data = taxDeclarationService.getDashboard(year);
        return ResponseEntity.ok(toDashboardResponse(data));
    }
    
    /**
     * Báo cáo theo tháng
     */
    @GetMapping("/reports/monthly")
    public ResponseEntity<MonthlyTaxReportResponse> getMonthlyReport(
            @RequestParam Integer year
    ) {
        var report = taxDeclarationService.getMonthlyReport(year);
        return ResponseEntity.ok(report);
    }
    
    // ===== HELPER METHODS =====
    
    private TaxDeclarationResponse toDeclarationResponse(TaxDeclaration declaration) {
        return TaxDeclarationResponse.builder()
                .id(declaration.getId())
                .declarationCode(declaration.getDeclarationCode())
                .declarationType(declaration.getDeclarationType())
                .taxFormVersion(declaration.getTaxFormVersion())
                .taxPeriodType(declaration.getTaxPeriodType())
                .taxPeriodYear(declaration.getTaxPeriodYear())
                .taxPeriodMonth(declaration.getTaxPeriodMonth())
                .taxPeriodQuarter(declaration.getTaxPeriodQuarter())
                .companyName(declaration.getCompanyName())
                .companyTaxCode(declaration.getCompanyTaxCode())
                .taxDepartment(declaration.getTaxDepartment())
                .totalEmployeeCount(declaration.getTotalEmployeeCount())
                .totalTaxableIncome(declaration.getTotalTaxableIncome())
                .totalTaxWithheld(declaration.getTotalTaxWithheld())
                .totalTaxPaid(declaration.getTotalTaxPaid())
                .totalTaxDue(declaration.getTotalTaxDue())
                .totalTaxRefund(declaration.getTotalTaxRefund())
                .xmlFileUrl(declaration.getXmlFileUrl())
                .pdfFileUrl(declaration.getPdfFileUrl())
                .excelFileUrl(declaration.getExcelFileUrl())
                .status(declaration.getStatus())
                .submittedAt(declaration.getSubmittedAt())
                .submittedBy(declaration.getSubmittedBy())
                .acceptedAt(declaration.getAcceptedAt())
                .acceptanceCode(declaration.getAcceptanceCode())
                .notes(declaration.getNotes())
                .rejectionReason(declaration.getRejectionReason())
                .build();
    }
    
    private TaxPayoutRecordResponse toPayoutRecordResponse(TaxPayoutRecord record) {
        return TaxPayoutRecordResponse.builder()
                .id(record.getId())
                .userId(record.getUser().getId())
                .userFullName(record.getUserFullName())
                .userCccd(record.getUserCccd())
                .payoutSource(record.getPayoutSource())
                .contractId(record.getContract() != null ? record.getContract().getId() : null)
                .milestoneId(record.getMilestone() != null ? record.getMilestone().getId() : null)
                .grossAmount(record.getGrossAmount())
                .taxAmount(record.getTaxAmount())
                .netAmount(record.getNetAmount())
                .taxRate(record.getTaxRate())
                .payoutDate(record.getPayoutDate())
                .taxPeriodYear(record.getTaxPeriodYear())
                .taxPeriodMonth(record.getTaxPeriodMonth())
                .taxPeriodQuarter(record.getTaxPeriodQuarter())
                .status(record.getStatus())
                .isTaxDeclared(record.getIsTaxDeclared())
                .taxDeclarationDate(record.getTaxDeclarationDate())
                .description(record.getDescription())
                .build();
    }
    
    private UserTaxSummaryResponse toTaxSummaryResponse(UserTaxSummary summary) {
        return UserTaxSummaryResponse.builder()
                .id(summary.getId())
                .userId(summary.getUser().getId())
                .userFullName(summary.getUserFullName())
                .userCccd(summary.getUserCccd())
                .userTaxCode(summary.getUserTaxCode())
                .taxPeriodType(summary.getTaxPeriodType())
                .taxPeriodYear(summary.getTaxPeriodYear())
                .taxPeriodMonth(summary.getTaxPeriodMonth())
                .taxPeriodQuarter(summary.getTaxPeriodQuarter())
                .periodStartDate(summary.getPeriodStartDate())
                .periodEndDate(summary.getPeriodEndDate())
                .totalGrossIncome(summary.getTotalGrossIncome())
                .totalTaxableIncome(summary.getTotalTaxableIncome())
                .incomeFromMilestone(summary.getIncomeFromMilestone())
                .incomeFromTermination(summary.getIncomeFromTermination())
                .incomeFromRefund(summary.getIncomeFromRefund())
                .incomeFromOther(summary.getIncomeFromOther())
                .totalTaxWithheld(summary.getTotalTaxWithheld())
                .totalTaxPaid(summary.getTotalTaxPaid())
                .totalTaxRefunded(summary.getTotalTaxRefunded())
                .totalTaxDue(summary.getTotalTaxDue())
                .effectiveTaxRate(summary.getEffectiveTaxRate())
                .totalPayoutCount(summary.getTotalPayoutCount())
                .totalContractCount(summary.getTotalContractCount())
                .totalWithdrawalCount(summary.getTotalWithdrawalCount())
                .status(summary.getStatus())
                .build();
    }
    
    private TaxDashboardResponse toDashboardResponse(TaxDashboardData data) {
        // TODO: Implement mapping
        return TaxDashboardResponse.builder().build();
    }
    
    // ===== DTOs =====
    
    @lombok.Data
    public static class TaxDeclarationRequest {
        private com.fpt.producerworkbench.common.TaxPeriodType periodType;
        private Integer year;
        private Integer monthOrQuarter;
    }
    
    @lombok.Data
    public static class MarkDeclaredRequest {
        private java.util.List<Long> payoutIds;
        private Long declarationId;
    }
    
    @lombok.Data
    @lombok.Builder
    public static class TaxDashboardResponse {
        private Integer year;
        private Integer totalUsers;
        private java.math.BigDecimal totalIncome;
        private java.math.BigDecimal totalTaxCollected;
        private java.math.BigDecimal averageTaxRate;
    }
    
    @lombok.Data
    @lombok.Builder
    public static class MonthlyTaxReportResponse {
        private Integer year;
        private java.util.List<MonthData> months;
        
        @lombok.Data
        @lombok.Builder
        public static class MonthData {
            private Integer month;
            private java.math.BigDecimal totalIncome;
            private java.math.BigDecimal totalTax;
            private Integer userCount;
        }
    }
    
    @lombok.Data
    public static class TaxDashboardData {
        // TODO: Define dashboard data structure
    }
}


