package com.fpt.producerworkbench.service.impl;

import com.fpt.producerworkbench.common.PayoutSource;
import com.fpt.producerworkbench.common.PayoutStatus;
import com.fpt.producerworkbench.dto.response.TaxOverviewResponse;
import com.fpt.producerworkbench.dto.response.TaxSeriesPointResponse;
import com.fpt.producerworkbench.dto.response.TaxSourceBreakdownResponse;
import com.fpt.producerworkbench.dto.response.TaxTransactionResponse;
import com.fpt.producerworkbench.entity.Contract;
import com.fpt.producerworkbench.entity.Milestone;
import com.fpt.producerworkbench.entity.Project;
import com.fpt.producerworkbench.entity.TaxPayoutRecord;
import com.fpt.producerworkbench.entity.User;
import com.fpt.producerworkbench.exception.AppException;
import com.fpt.producerworkbench.exception.ErrorCode;
import com.fpt.producerworkbench.repository.TaxPayoutRecordRepository;
import com.fpt.producerworkbench.repository.UserRepository;
import com.fpt.producerworkbench.service.TaxViewService;
import com.fpt.producerworkbench.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;

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
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;

@Service
@RequiredArgsConstructor
@Slf4j
public class TaxViewServiceImpl implements TaxViewService {

    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final DateTimeFormatter YEAR_FORMAT = DateTimeFormatter.ofPattern("yyyy");

    private final TaxPayoutRecordRepository taxPayoutRecordRepository;
    private final UserRepository userRepository;
    @Autowired(required = false)
    private TemplateEngine templateEngine;

    @Override
    @Transactional(readOnly = true)
    public TaxOverviewResponse getOverview(LocalDate fromDate, LocalDate toDate, String groupBy) {
        User user = getCurrentUser();

        List<TaxPayoutRecord> records = taxPayoutRecordRepository
                .findByUserIdAndPayoutDateBetween(user.getId(), fromDate, toDate)
                .stream()
                .filter(r -> r.getStatus() == PayoutStatus.COMPLETED)
                .toList();

        BigDecimal totalGross = BigDecimal.ZERO;
        BigDecimal totalTax = BigDecimal.ZERO;
        long totalPayoutCount = records.size();
        Set<Long> contractIds = records.stream()
                .map(r -> Optional.ofNullable(r.getContract()).map(Contract::getId).orElse(null))
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        Set<Long> projectIds = records.stream()
                .map(r -> Optional.ofNullable(r.getContract())
                        .map(Contract::getProject)
                        .map(Project::getId)
                        .orElse(null))
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());

        Map<PayoutSource, SourceAccumulator> sourceAccumulator = new EnumMap<>(PayoutSource.class);
        Map<String, SourceAccumulator> timeAccumulator = new HashMap<>();

        for (TaxPayoutRecord record : records) {
            BigDecimal gross = defaultBigDecimal(record.getGrossAmount());
            BigDecimal tax = defaultBigDecimal(record.getTaxAmount());
            totalGross = totalGross.add(gross);
            totalTax = totalTax.add(tax);

            // source breakdown
            PayoutSource source = record.getPayoutSource();
            SourceAccumulator sa = sourceAccumulator.computeIfAbsent(source, k -> new SourceAccumulator());
            sa.gross = sa.gross.add(gross);
            sa.tax = sa.tax.add(tax);
            sa.net = sa.net.add(defaultBigDecimal(record.getNetAmount()));
            sa.count++;

            // time series
            String key = formatPeriod(record.getPayoutDate(), groupBy);
            SourceAccumulator ta = timeAccumulator.computeIfAbsent(key, k -> new SourceAccumulator());
            ta.gross = ta.gross.add(gross);
            ta.tax = ta.tax.add(tax);
            ta.net = ta.net.add(defaultBigDecimal(record.getNetAmount()));
        }

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

        List<TaxSeriesPointResponse> series = timeAccumulator.entrySet().stream()
                .map(e -> TaxSeriesPointResponse.builder()
                        .periodLabel(e.getKey())
                        .gross(e.getValue().gross)
                        .tax(e.getValue().tax)
                        .net(e.getValue().net)
                        .build())
                .sorted(Comparator.comparing(TaxSeriesPointResponse::getPeriodLabel))
                .toList();

        BigDecimal totalNet = totalGross.subtract(totalTax);

        return TaxOverviewResponse.builder()
                .totalGross(totalGross)
                .totalTax(totalTax)
                .totalNet(totalNet)
                .totalPayoutCount(totalPayoutCount)
                .totalContractCount(contractIds.size())
                .totalProjectCount(projectIds.size())
                .sourceBreakdown(sourceBreakdown)
                .timeSeries(series)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<TaxTransactionResponse> getTransactions(
            LocalDate fromDate,
            LocalDate toDate,
            PayoutSource source,
            Pageable pageable
    ) {
        User user = getCurrentUser();

        Page<TaxPayoutRecord> page = taxPayoutRecordRepository.findByUserAndDateRange(
                user.getId(),
                fromDate,
                toDate,
                source,
                PayoutStatus.COMPLETED,
                pageable
        );

        return page.map(this::toTransactionResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public TaxViewService.ExportedFile exportTransactions(
            LocalDate fromDate,
            LocalDate toDate,
            PayoutSource source,
            String format
    ) {
        User user = getCurrentUser();
        String normalizedFormat = format != null ? format.trim().toUpperCase() : "CSV";

        List<TaxPayoutRecord> records = taxPayoutRecordRepository.findByUserIdAndPayoutDateBetween(
                        user.getId(), fromDate, toDate)
                .stream()
                .filter(r -> r.getStatus() == PayoutStatus.COMPLETED)
                .filter(r -> source == null || r.getPayoutSource() == source)
                .toList();

        return switch (normalizedFormat) {
            case "CSV" -> exportCsv(records);
            case "XLSX" -> exportXlsx(records);
            case "PDF" -> exportPdf(records, user);
            default -> throw new AppException(ErrorCode.BAD_REQUEST,
                    "format không hỗ trợ: " + normalizedFormat);
        };
    }

    private TaxTransactionResponse toTransactionResponse(TaxPayoutRecord record) {
        Contract contract = record.getContract();
        Project project = contract != null ? contract.getProject() : null;
        Milestone milestone = record.getMilestone();

        return TaxTransactionResponse.builder()
                .id(record.getId())
                .payoutDate(record.getPayoutDate())
                .payoutSource(record.getPayoutSource())
                .status(record.getStatus())
                .referenceCode(record.getReferenceCode())
                .grossAmount(defaultBigDecimal(record.getGrossAmount()))
                .taxAmount(defaultBigDecimal(record.getTaxAmount()))
                .netAmount(defaultBigDecimal(record.getNetAmount()))
                .projectId(project != null ? project.getId() : null)
                .projectTitle(project != null ? project.getTitle() : null)
                .contractId(contract != null ? contract.getId() : null)
                .milestoneId(milestone != null ? milestone.getId() : null)
                .milestoneTitle(milestone != null ? milestone.getTitle() : null)
                .taxPeriodMonth(record.getTaxPeriodMonth())
                .taxPeriodYear(record.getTaxPeriodYear())
                .taxPeriodQuarter(record.getTaxPeriodQuarter())
                .build();
    }

    private String formatPeriod(LocalDate date, String groupBy) {
        if ("YEAR".equalsIgnoreCase(groupBy)) {
            return YEAR_FORMAT.format(date);
        }
        // default: MONTH
        return MONTH_FORMAT.format(date);
    }

    private BigDecimal defaultBigDecimal(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private TaxViewService.ExportedFile exportCsv(List<TaxPayoutRecord> records) {
        StringBuilder sb = new StringBuilder();
        sb.append("PayoutDate,Source,Project,ContractId,Milestone,ReferenceCode,Gross,Tax,Net,Status,TaxPeriodYear,TaxPeriodMonth,TaxPeriodQuarter\n");
        for (TaxPayoutRecord r : records) {
            Contract c = r.getContract();
            Project p = c != null ? c.getProject() : null;
            Milestone m = r.getMilestone();

            sb.append(csv(r.getPayoutDate()))
                    .append(',').append(csv(r.getPayoutSource()))
                    .append(',').append(csv(p != null ? p.getTitle() : null))
                    .append(',').append(csv(c != null ? c.getId() : null))
                    .append(',').append(csv(m != null ? m.getTitle() : null))
                    .append(',').append(csv(r.getReferenceCode()))
                    .append(',').append(csv(r.getGrossAmount()))
                    .append(',').append(csv(r.getTaxAmount()))
                    .append(',').append(csv(r.getNetAmount()))
                    .append(',').append(csv(r.getStatus()))
                    .append(',').append(csv(r.getTaxPeriodYear()))
                    .append(',').append(csv(r.getTaxPeriodMonth()))
                    .append(',').append(csv(r.getTaxPeriodQuarter()))
                    .append('\n');
        }

        byte[] bytes = sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String filename = "tax_transactions_" + LocalDate.now() + ".csv";
        return new TaxViewService.ExportedFile(bytes, filename, "text/csv");
    }

    private TaxViewService.ExportedFile exportXlsx(List<TaxPayoutRecord> records) {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Transactions");
            String[] headers = {
                    "PayoutDate", "Source", "Project", "ContractId", "Milestone",
                    "ReferenceCode", "Gross", "Tax", "Net", "Status",
                    "TaxPeriodYear", "TaxPeriodMonth", "TaxPeriodQuarter"
            };

            Row header = sheet.createRow(0);
            CellStyle headerStyle = wb.createCellStyle();
            headerStyle.setAlignment(HorizontalAlignment.CENTER);

            for (int i = 0; i < headers.length; i++) {
                org.apache.poi.ss.usermodel.Cell cell = header.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            int rowIdx = 1;
            for (TaxPayoutRecord r : records) {
                Contract c = r.getContract();
                Project p = c != null ? c.getProject() : null;
                Milestone m = r.getMilestone();

                Row row = sheet.createRow(rowIdx++);
                int col = 0;
                row.createCell(col++).setCellValue(stringOrEmpty(r.getPayoutDate()));
                row.createCell(col++).setCellValue(stringOrEmpty(r.getPayoutSource()));
                row.createCell(col++).setCellValue(stringOrEmpty(p != null ? p.getTitle() : null));
                row.createCell(col++).setCellValue(stringOrEmpty(c != null ? c.getId() : null));
                row.createCell(col++).setCellValue(stringOrEmpty(m != null ? m.getTitle() : null));
                row.createCell(col++).setCellValue(stringOrEmpty(r.getReferenceCode()));
                row.createCell(col++).setCellValue(numberOrZero(r.getGrossAmount()));
                row.createCell(col++).setCellValue(numberOrZero(r.getTaxAmount()));
                row.createCell(col++).setCellValue(numberOrZero(r.getNetAmount()));
                row.createCell(col++).setCellValue(stringOrEmpty(r.getStatus()));
                row.createCell(col++).setCellValue(stringOrEmpty(r.getTaxPeriodYear()));
                row.createCell(col++).setCellValue(stringOrEmpty(r.getTaxPeriodMonth()));
                row.createCell(col++).setCellValue(stringOrEmpty(r.getTaxPeriodQuarter()));
            }

            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            wb.write(bos);
            String filename = "tax_transactions_" + LocalDate.now() + ".xlsx";
            return new TaxViewService.ExportedFile(bos.toByteArray(), filename,
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        } catch (Exception e) {
            throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR, "Xuất XLSX lỗi: " + e.getMessage());
        }
    }

    private TaxViewService.ExportedFile exportPdf(List<TaxPayoutRecord> records, User user) {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            List<TaxViewService.TransactionExportRow> rows = records.stream().map(r -> {
                Contract c = r.getContract();
                Project p = c != null ? c.getProject() : null;
                return TaxViewService.TransactionExportRow.builder()
                        .payoutDate(r.getPayoutDate())
                        .source(r.getPayoutSource())
                        .projectTitle(p != null ? p.getTitle() : "")
                        .grossAmount(defaultBigDecimal(r.getGrossAmount()))
                        .taxAmount(defaultBigDecimal(r.getTaxAmount()))
                        .netAmount(defaultBigDecimal(r.getNetAmount()))
                        .status(r.getStatus())
                        .taxPeriodYear(r.getTaxPeriodYear())
                        .taxPeriodMonth(r.getTaxPeriodMonth())
                        .build();
            }).toList();

            BigDecimal totalGross = rows.stream().map(TaxViewService.TransactionExportRow::getGrossAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal totalTax = rows.stream().map(TaxViewService.TransactionExportRow::getTaxAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal totalNet = rows.stream().map(TaxViewService.TransactionExportRow::getNetAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            if (templateEngine != null) {
                Context ctx = new Context();
                ctx.setVariable("userName", user.getFullName());
                ctx.setVariable("userEmail", user.getEmail());
                ctx.setVariable("exportDate", LocalDate.now());
                ctx.setVariable("fromDate", rows.isEmpty() ? "" : rows.get(0).getPayoutDate());
                ctx.setVariable("toDate", rows.isEmpty() ? "" : rows.get(rows.size() - 1).getPayoutDate());
                ctx.setVariable("totalGross", totalGross);
                ctx.setVariable("totalTax", totalTax);
                ctx.setVariable("totalNet", totalNet);
                ctx.setVariable("totalCount", rows.size());
                ctx.setVariable("rows", rows);

                String html = templateEngine.process("tax/transactions", ctx);
                PdfRendererBuilder builder = new PdfRendererBuilder();
                builder.useFastMode();
                // Embed font to render tiếng Việt properly
                try {
                    byte[] fontBytes = StreamUtils.copyToByteArray(
                            new ClassPathResource("templates/NotoSerif-Regular.ttf").getInputStream());
                    builder.useFont(() -> new ByteArrayInputStream(fontBytes), "Noto Serif");
                } catch (Exception ignored) {
                    // fallback to default font if missing
                }
                builder.withHtmlContent(html, null);
                builder.toStream(bos);
                builder.run();
            } else {
                // fallback to simple table if templateEngine missing
                PdfWriter writer = new PdfWriter(bos);
                PdfDocument pdf = new PdfDocument(writer);
                Document doc = new Document(pdf, PageSize.A4.rotate());

                doc.add(new Paragraph("Bảng kê Thuế & Thu nhập").setBold().setFontSize(14));
                doc.add(new Paragraph("Người dùng: " + user.getFullName() + " (" + user.getEmail() + ")")
                        .setFontSize(10));
                doc.add(new Paragraph("Ngày xuất: " + LocalDate.now()).setFontSize(9));
                doc.add(new Paragraph(" ").setFontSize(4));

                float[] widths = {80, 100, 140, 80, 80, 80, 70, 70, 70};
                Table table = new Table(widths);
                String[] headers = {
                        "Ngày chi trả",
                        "Nguồn",
                        "Dự án",
                        "Tổng trước thuế",
                        "Thuế khấu trừ",
                        "Thực nhận",
                        "Trạng thái",
                        "Năm thuế",
                        "Tháng thuế"
                };
                for (String h : headers) {
                    Cell cell = new Cell().add(new Paragraph(h)).setBold()
                            .setBackgroundColor(ColorConstants.LIGHT_GRAY);
                    table.addHeaderCell(cell);
                }

                for (TaxViewService.TransactionExportRow r : rows) {
                    addCell(table, r.getPayoutDate());
                    addCell(table, r.getSource());
                    addCell(table, r.getProjectTitle());
                    addCell(table, r.getGrossAmount());
                    addCell(table, r.getTaxAmount());
                    addCell(table, r.getNetAmount());
                    addCell(table, r.getStatus());
                    addCell(table, r.getTaxPeriodYear());
                    addCell(table, r.getTaxPeriodMonth());
                }

                doc.add(table);
                doc.close();
            }

            String filename = "tax_transactions_" + LocalDate.now() + ".pdf";
            return new TaxViewService.ExportedFile(bos.toByteArray(), filename, "application/pdf");
        } catch (Exception e) {
            throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR, "Xuất PDF lỗi: " + e.getMessage());
        }
    }

    private String stringOrEmpty(Object o) {
        return o == null ? "" : o.toString();
    }

    private double numberOrZero(BigDecimal bd) {
        return bd != null ? bd.doubleValue() : 0d;
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

    private void addCell(Table table, Object value) {
        table.addCell(new Cell().add(new Paragraph(stringOrEmpty(value))));
    }

    private User getCurrentUser() {
        String email = SecurityUtils.getCurrentUserLogin()
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHENTICATED));
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
    }

    private static class SourceAccumulator {
        BigDecimal gross = BigDecimal.ZERO;
        BigDecimal tax = BigDecimal.ZERO;
        BigDecimal net = BigDecimal.ZERO;
        long count = 0;
    }
}

