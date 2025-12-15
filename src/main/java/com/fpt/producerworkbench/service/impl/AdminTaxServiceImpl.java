package com.fpt.producerworkbench.service.impl;

import com.fpt.producerworkbench.common.PayoutSource;
import com.fpt.producerworkbench.common.PayoutStatus;
import com.fpt.producerworkbench.dto.response.AdminTaxOverviewResponse;
import com.fpt.producerworkbench.dto.response.AdminTaxPayoutResponse;
import com.fpt.producerworkbench.dto.response.AdminTaxSeriesPointResponse;
import com.fpt.producerworkbench.dto.response.TaxSourceBreakdownResponse;
import com.fpt.producerworkbench.entity.Contract;
import com.fpt.producerworkbench.entity.Milestone;
import com.fpt.producerworkbench.entity.Project;
import com.fpt.producerworkbench.entity.TaxPayoutRecord;
import com.fpt.producerworkbench.repository.TaxPayoutRecordRepository;
import com.fpt.producerworkbench.service.AdminTaxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import com.fpt.producerworkbench.exception.AppException;
import com.fpt.producerworkbench.exception.ErrorCode;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminTaxServiceImpl implements AdminTaxService {

    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final DateTimeFormatter YEAR_FORMAT = DateTimeFormatter.ofPattern("yyyy");

    private final TaxPayoutRecordRepository taxPayoutRecordRepository;
    private final TemplateEngine templateEngine;

    @Override
    @Transactional(readOnly = true)
    public AdminTaxOverviewResponse getOverview(LocalDate from, LocalDate to, String groupBy) {
        List<TaxPayoutRecord> records = taxPayoutRecordRepository
                .findAllByPayoutDateBetween(from, to)
                .stream()
                .filter(r -> r.getStatus() == PayoutStatus.COMPLETED)
                .toList();

        BigDecimal totalGross = BigDecimal.ZERO;
        BigDecimal totalTaxWithheld = BigDecimal.ZERO;
        BigDecimal totalTaxPaid = BigDecimal.ZERO; // hiện chưa có cột paidAmount, tạm = taxPaid ? taxAmount : 0
        long totalPayoutCount = records.size();

        Set<Long> projectIds = records.stream()
                .map(r -> r.getContract() != null ? r.getContract().getProject().getId() : null)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<Long> userIds = records.stream()
                .map(r -> r.getUser() != null ? r.getUser().getId() : null)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<PayoutSource, SourceAccumulator> sourceAccumulator = new EnumMap<>(PayoutSource.class);
        Map<String, SourceAccumulator> timeAccumulator = new HashMap<>();

        for (TaxPayoutRecord record : records) {
            BigDecimal gross = nz(record.getGrossAmount());
            BigDecimal tax = nz(record.getTaxAmount());
            totalGross = totalGross.add(gross);
            totalTaxWithheld = totalTaxWithheld.add(tax);

            if (Boolean.TRUE.equals(record.getTaxPaid())) {
                totalTaxPaid = totalTaxPaid.add(tax);
            }

            // source breakdown
            PayoutSource source = record.getPayoutSource();
            SourceAccumulator sa = sourceAccumulator.computeIfAbsent(source, k -> new SourceAccumulator());
            sa.gross = sa.gross.add(gross);
            sa.tax = sa.tax.add(tax);
            sa.net = sa.net.add(nz(record.getNetAmount()));
            sa.count++;

            // time series
            String key = formatPeriod(record.getPayoutDate(), groupBy);
            SourceAccumulator ta = timeAccumulator.computeIfAbsent(key, k -> new SourceAccumulator());
            ta.gross = ta.gross.add(gross);
            ta.tax = ta.tax.add(tax);
            ta.net = ta.net.add(nz(record.getNetAmount()));
            if (Boolean.TRUE.equals(record.getTaxPaid())) {
                ta.paid = ta.paid.add(tax);
            }
        }

        BigDecimal totalTaxDue = totalTaxWithheld.subtract(totalTaxPaid);

        List<TaxSourceBreakdownResponse> sourceBreakdown = sourceAccumulator.entrySet().stream()
                .map(e -> TaxSourceBreakdownResponse.builder()
                        .source(e.getKey())
                        .gross(e.getValue().gross)
                        .tax(e.getValue().tax)
                        .net(e.getValue().net)
                        .payoutCount(e.getValue().count)
                        .build())
                .sorted(Comparator.comparing(TaxSourceBreakdownResponse::getGross).reversed())
                .toList();

        List<AdminTaxSeriesPointResponse> series = timeAccumulator.entrySet().stream()
                .map(e -> AdminTaxSeriesPointResponse.builder()
                        .periodLabel(e.getKey())
                        .gross(e.getValue().gross)
                        .taxWithheld(e.getValue().tax)
                        .taxPaid(e.getValue().paid)
                        .taxDue(e.getValue().tax.subtract(e.getValue().paid))
                        .build())
                .sorted(Comparator.comparing(AdminTaxSeriesPointResponse::getPeriodLabel))
                .toList();

        return AdminTaxOverviewResponse.builder()
                .totalGross(totalGross)
                .totalTaxWithheld(totalTaxWithheld)
                .totalTaxPaid(totalTaxPaid)
                .totalTaxDue(totalTaxDue)
                .totalPayoutCount(totalPayoutCount)
                .totalUserCount(userIds.size())
                .totalProjectCount(projectIds.size())
                .sourceBreakdown(sourceBreakdown)
                .timeSeries(series)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AdminTaxPayoutResponse> getPayouts(LocalDate from,
                                                   LocalDate to,
                                                   Integer month,
                                                   Integer year,
                                                   Integer quarter,
                                                   Long userId,
                                                   Long projectId,
                                                   Long contractId,
                                                   PayoutSource source,
                                                   Boolean declared,
                                                   Boolean paid,
                                                   Pageable pageable) {
        Page<TaxPayoutRecord> page = taxPayoutRecordRepository.findAdminPayouts(
                from, to, month, year, quarter, userId, projectId, contractId, source, declared, paid, pageable
        );
        List<AdminTaxPayoutResponse> mapped = page.getContent().stream()
                .map(this::toResponse)
                .toList();
        return new PageImpl<>(mapped, pageable, page.getTotalElements());
    }

    @Override
    @Transactional(readOnly = true)
    public ExportedFile exportPayouts(LocalDate from,
                                      LocalDate to,
                                      Integer month,
                                      Integer year,
                                      Integer quarter,
                                      Long userId,
                                      Long projectId,
                                      Long contractId,
                                      PayoutSource source,
                                      Boolean declared,
                                      Boolean paid,
                                      String format) {
        List<TaxPayoutRecord> records = taxPayoutRecordRepository.findAdminPayouts(
                        from, to, month, year, quarter, userId, projectId, contractId, source, declared, paid,
                        Pageable.unpaged())
                .getContent();

        String normalized = format != null ? format.trim().toUpperCase() : "CSV";
        return switch (normalized) {
            case "CSV" -> exportCsv(records);
            case "XLSX" -> exportXlsx(records);
            case "PDF" -> exportPdf(records);
            default -> throw new AppException(ErrorCode.BAD_REQUEST, "format không hỗ trợ: " + normalized);
        };
    }

    @Override
    @Transactional
    public void markPayouts(List<Long> ids, Boolean declared, Boolean paid) {
        if (ids == null || ids.isEmpty()) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Danh sách ids trống");
        }
        Iterable<TaxPayoutRecord> iterable = taxPayoutRecordRepository.findAllById(ids);
        List<TaxPayoutRecord> list = StreamSupport.stream(iterable.spliterator(), false)
                .filter(r -> r.getStatus() == PayoutStatus.COMPLETED)
                .toList();
        for (TaxPayoutRecord r : list) {
            if (declared != null) {
                r.setIsTaxDeclared(declared);
            }
            if (paid != null) {
                r.setTaxPaid(paid);
            }
        }
        taxPayoutRecordRepository.saveAll(list);
    }

    private AdminTaxPayoutResponse toResponse(TaxPayoutRecord r) {
        Contract c = r.getContract();
        Project p = c != null ? c.getProject() : null;
        Milestone m = r.getMilestone();
        return AdminTaxPayoutResponse.builder()
                .id(r.getId())
                .payoutDate(r.getPayoutDate())
                .payoutSource(r.getPayoutSource())
                .status(r.getStatus())
                .grossAmount(nz(r.getGrossAmount()))
                .taxAmount(nz(r.getTaxAmount()))
                .netAmount(nz(r.getNetAmount()))
                .userId(r.getUser() != null ? r.getUser().getId() : null)
                .userName(r.getUser() != null ? r.getUser().getFullName() : null)
                .userEmail(r.getUser() != null ? r.getUser().getEmail() : null)
                .userCccd(r.getUser() != null ? r.getUser().getCccdNumber() : null)
                .projectId(p != null ? p.getId() : null)
                .projectTitle(p != null ? p.getTitle() : null)
                .contractId(c != null ? c.getId() : null)
                .milestoneId(m != null ? m.getId() : null)
                .milestoneTitle(m != null ? m.getTitle() : null)
                .taxPeriodMonth(r.getTaxPeriodMonth())
                .taxPeriodYear(r.getTaxPeriodYear())
                .taxPeriodQuarter(r.getTaxPeriodQuarter())
                .taxDeclared(r.getIsTaxDeclared())
                .taxPaid(r.getTaxPaid())
                .build();
    }

    private String formatPeriod(LocalDate date, String groupBy) {
        if (date == null) return "";
        if ("YEAR".equalsIgnoreCase(groupBy)) {
            return YEAR_FORMAT.format(date);
        }
        return MONTH_FORMAT.format(date);
    }

    private BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    // ===== Export helpers =====
    private ExportedFile exportCsv(List<TaxPayoutRecord> records) {
        StringBuilder sb = new StringBuilder();
        sb.append("PayoutDate,Source,User,Email,Project,ContractId,Milestone,Gross,Tax,Net,Status,Declared,Paid,TaxYear,TaxMonth,TaxQuarter\n");
        for (TaxPayoutRecord r : records) {
            Contract c = r.getContract();
            Project p = c != null ? c.getProject() : null;
            Milestone m = r.getMilestone();
            sb.append(csv(r.getPayoutDate()))
                    .append(',').append(csv(r.getPayoutSource()))
                    .append(',').append(csv(r.getUser() != null ? r.getUser().getFullName() : null))
                    .append(',').append(csv(r.getUser() != null ? r.getUser().getEmail() : null))
                    .append(',').append(csv(p != null ? p.getTitle() : null))
                    .append(',').append(csv(c != null ? c.getId() : null))
                    .append(',').append(csv(m != null ? m.getTitle() : null))
                    .append(',').append(csv(r.getGrossAmount()))
                    .append(',').append(csv(r.getTaxAmount()))
                    .append(',').append(csv(r.getNetAmount()))
                    .append(',').append(csv(r.getStatus()))
                    .append(',').append(csv(r.getIsTaxDeclared()))
                    .append(',').append(csv(r.getTaxPaid()))
                    .append(',').append(csv(r.getTaxPeriodYear()))
                    .append(',').append(csv(r.getTaxPeriodMonth()))
                    .append(',').append(csv(r.getTaxPeriodQuarter()))
                    .append('\n');
        }
        byte[] bytes = sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String filename = "admin_tax_payouts_" + LocalDate.now() + ".csv";
        return new ExportedFile(bytes, filename, "text/csv");
    }

    private ExportedFile exportXlsx(List<TaxPayoutRecord> records) {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Payouts");
            String[] headers = {
                    "PayoutDate", "Source", "User", "Email", "Project", "ContractId", "Milestone",
                    "Gross", "Tax", "Net", "Status", "Declared", "Paid",
                    "TaxYear", "TaxMonth", "TaxQuarter"
            };
            Row header = sheet.createRow(0);
            CellStyle style = wb.createCellStyle();
            style.setAlignment(HorizontalAlignment.CENTER);
            for (int i = 0; i < headers.length; i++) {
                org.apache.poi.ss.usermodel.Cell cell = header.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(style);
            }
            int rowIdx = 1;
            for (TaxPayoutRecord r : records) {
                Contract c = r.getContract();
                Project p = c != null ? c.getProject() : null;
                Milestone m = r.getMilestone();
                Row row = sheet.createRow(rowIdx++);
                int col = 0;
                row.createCell(col++).setCellValue(str(r.getPayoutDate()));
                row.createCell(col++).setCellValue(str(r.getPayoutSource()));
                row.createCell(col++).setCellValue(str(r.getUser() != null ? r.getUser().getFullName() : null));
                row.createCell(col++).setCellValue(str(r.getUser() != null ? r.getUser().getEmail() : null));
                row.createCell(col++).setCellValue(str(p != null ? p.getTitle() : null));
                row.createCell(col++).setCellValue(str(c != null ? c.getId() : null));
                row.createCell(col++).setCellValue(str(m != null ? m.getTitle() : null));
                row.createCell(col++).setCellValue(num(r.getGrossAmount()));
                row.createCell(col++).setCellValue(num(r.getTaxAmount()));
                row.createCell(col++).setCellValue(num(r.getNetAmount()));
                row.createCell(col++).setCellValue(str(r.getStatus()));
                row.createCell(col++).setCellValue(bool(r.getIsTaxDeclared()));
                row.createCell(col++).setCellValue(bool(r.getTaxPaid()));
                row.createCell(col++).setCellValue(str(r.getTaxPeriodYear()));
                row.createCell(col++).setCellValue(str(r.getTaxPeriodMonth()));
                row.createCell(col++).setCellValue(str(r.getTaxPeriodQuarter()));
            }
            for (int i = 0; i < headers.length; i++) sheet.autoSizeColumn(i);
            wb.write(bos);
            String filename = "admin_tax_payouts_" + LocalDate.now() + ".xlsx";
            return new ExportedFile(bos.toByteArray(), filename,
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        } catch (Exception e) {
            throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR, "Xuất XLSX lỗi: " + e.getMessage());
        }
    }

    private ExportedFile exportPdf(List<TaxPayoutRecord> records) {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            // map rows for template
            List<Map<String, Object>> rows = records.stream().map(r -> {
                Contract c = r.getContract();
                Project p = c != null ? c.getProject() : null;
                Milestone m = r.getMilestone();
                Map<String, Object> row = new HashMap<>();
                row.put("payoutDate", r.getPayoutDate());
                row.put("source", r.getPayoutSource());
                row.put("userName", r.getUser() != null ? r.getUser().getFullName() : "");
                row.put("userEmail", r.getUser() != null ? r.getUser().getEmail() : "");
                row.put("projectTitle", p != null ? p.getTitle() : "");
                row.put("contractId", c != null ? c.getId() : "");
                row.put("milestoneTitle", m != null ? m.getTitle() : "");
                row.put("grossAmount", nz(r.getGrossAmount()));
                row.put("taxAmount", nz(r.getTaxAmount()));
                row.put("netAmount", nz(r.getNetAmount()));
                row.put("status", r.getStatus());
                row.put("taxDeclared", r.getIsTaxDeclared());
                row.put("taxPaid", r.getTaxPaid());
                row.put("taxPeriodYear", r.getTaxPeriodYear());
                row.put("taxPeriodMonth", r.getTaxPeriodMonth());
                row.put("taxPeriodQuarter", r.getTaxPeriodQuarter());
                return row;
            }).toList();

            BigDecimal totalGross = rows.stream().map(r -> (BigDecimal) r.get("grossAmount"))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal totalTax = rows.stream().map(r -> (BigDecimal) r.get("taxAmount"))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            Context ctx = new Context();
            ctx.setVariable("fromDate", records.isEmpty() ? "" : rows.get(0).get("payoutDate"));
            ctx.setVariable("toDate", records.isEmpty() ? "" : rows.get(rows.size() - 1).get("payoutDate"));
            ctx.setVariable("exportDate", LocalDate.now());
            ctx.setVariable("totalGross", totalGross);
            ctx.setVariable("totalTaxWithheld", totalTax);
            ctx.setVariable("totalTaxPaid", totalTax); // nếu cần phân biệt, cập nhật sau
            ctx.setVariable("totalTaxDue", totalTax.subtract(totalTax)); // placeholder
            ctx.setVariable("rows", rows);

            String html = templateEngine.process("admin/tax/payouts", ctx);
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            try {
                byte[] fontBytes = StreamUtils.copyToByteArray(
                        new ClassPathResource("templates/NotoSerif-Regular.ttf").getInputStream());
                builder.useFont(() -> new ByteArrayInputStream(fontBytes), "Noto Serif");
            } catch (Exception ignored) {
            }
            builder.withHtmlContent(html, null);
            builder.toStream(bos);
            builder.run();

            String filename = "admin_tax_payouts_" + LocalDate.now() + ".pdf";
            return new ExportedFile(bos.toByteArray(), filename, "application/pdf");
        } catch (Exception e) {
            throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR, "Xuất PDF lỗi: " + e.getMessage());
        }
    }

    private String csv(Object value) {
        if (value == null) return "";
        String s = value.toString();
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            s = s.replace("\"", "\"\"");
            return "\"" + s + "\"";
        }
        return s;
    }

    private String str(Object o) {
        return o == null ? "" : o.toString();
    }

    private double num(BigDecimal bd) {
        return bd != null ? bd.doubleValue() : 0d;
    }

    private String bool(Boolean b) {
        return b == null ? "" : b ? "YES" : "NO";
    }

    private static class SourceAccumulator {
        BigDecimal gross = BigDecimal.ZERO;
        BigDecimal tax = BigDecimal.ZERO;
        BigDecimal net = BigDecimal.ZERO;
        BigDecimal paid = BigDecimal.ZERO;
        long count = 0;
    }
}

