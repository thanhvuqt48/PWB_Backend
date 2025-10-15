package com.fpt.producerworkbench.service.impl;

import com.fpt.producerworkbench.common.ContractDocumentType;
import com.fpt.producerworkbench.common.ContractStatus;
import com.fpt.producerworkbench.dto.event.NotificationEvent;
import com.fpt.producerworkbench.dto.request.ContractPdfFillRequest;
import com.fpt.producerworkbench.dto.request.MilestoneRequest;
import com.fpt.producerworkbench.entity.*;
import com.fpt.producerworkbench.exception.AppException;
import com.fpt.producerworkbench.exception.ErrorCode;
import com.fpt.producerworkbench.repository.ContractDocumentRepository;
import com.fpt.producerworkbench.repository.ContractRepository;
import com.fpt.producerworkbench.service.ContractPdfService;
import com.fpt.producerworkbench.service.StorageService;
import com.itextpdf.forms.PdfAcroForm;
import com.itextpdf.forms.fields.PdfFormField;
import com.itextpdf.io.font.FontProgram;
import com.itextpdf.io.font.FontProgramFactory;
import com.itextpdf.io.font.PdfEncodings;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.annot.PdfWidgetAnnotation;
import com.itextpdf.layout.Canvas;
import com.itextpdf.layout.element.Paragraph;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StreamUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.kafka.core.KafkaTemplate;

@Service
@Slf4j
public class ContractPdfServiceImpl implements ContractPdfService {

    // ===== 1. CÁC HẰNG SỐ VÀ DEPENDENCIES =====
    private static final BigDecimal DEFAULT_VAT_RATE = new BigDecimal("0.08");
    private static final float INCH = 72f;

    // Chỉ giữ lại các dependency cần thiết cho Giai đoạn 2
    private final ContractRepository contractRepository;
    private final ContractDocumentRepository contractDocumentRepository;
    private final StorageService storageService;
    private final KafkaTemplate<String, NotificationEvent> kafkaTemplate;

    @Value("${pwb.contract.template}")
    private final Resource templateResource;

    @Value("${pwb.contract.font}")
    private final Resource fontResource;


    public ContractPdfServiceImpl(
            ContractRepository contractRepository,
            ContractDocumentRepository contractDocumentRepository,
            StorageService storageService,
            KafkaTemplate<String, NotificationEvent> kafkaTemplate,
            @Value("${pwb.contract.template}") Resource templateResource, // << @Value đặt ở đây
            @Value("${pwb.contract.font}") Resource fontResource         // << @Value đặt ở đây
    ) {
        this.contractRepository = contractRepository;
        this.contractDocumentRepository = contractDocumentRepository;
        this.storageService = storageService;
        this.kafkaTemplate = kafkaTemplate;
        this.templateResource = templateResource;
        this.fontResource = fontResource;
    }

    // ===== 2. PHƯƠNG THỨC CHÍNH CỦA SERVICE =====
    @Override
    @Transactional
    public byte[] generateAndSendReviewPdf(Authentication auth, Long contractId, ContractPdfFillRequest req) {
        log.info("Bắt đầu tạo PDF và gửi review cho hợp đồng ID: {}", contractId);

        // BƯỚC 2.1: TÌM VÀ VALIDATE HỢP ĐỒNG
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new AppException(ErrorCode.CONTRACT_NOT_FOUND));

        if (contract.getStatus() != ContractStatus.DRAFT) {
            log.warn("Hợp đồng ID: {} không ở trạng thái DRAFT.", contractId);
            throw new AppException(ErrorCode.CONTRACT_NOT_IN_DRAFT_STATUS);
        }

        try (InputStream in = templateResource.getInputStream();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            // BƯỚC 2.2: TẠO FILE PDF BẰNG iTEXT (TOÀN BỘ LOGIC CỦA BẠN)
            MoneyTotals totals = computeTotals(req);

            PdfReader reader = new PdfReader(in);
            PdfWriter writer = new PdfWriter(baos);
            PdfDocument pdf = new PdfDocument(reader, writer);
            PdfFont font = loadUnicodeFont();
            PdfAcroForm acro = PdfAcroForm.getAcroForm(pdf, true);

            acro.setGenerateAppearance(true);
            for (PdfFormField f : acro.getFormFields().values()) {
                f.setFont(font).setFontSize(10f);
            }

            Map<String, String> fields = mapDtoToPdfFields(req);
            fields.put("Text Box 37", formatMoney(totals.sum));
            fields.put("Text Box 38", formatMoney(totals.vat));
            fields.put("Text Box 39", formatMoney(totals.grandTotal));
            if (Boolean.TRUE.equals(req.getPayOnce())) {
                fields.put("Text Box 42", formatMoney(totals.grandTotal));
            }
            fields.put("Percent", ns(req.getPercent()));
            if (req.getFpEditAmount() != null) {
                fields.put("FP Edit Amount", String.valueOf(req.getFpEditAmount()));
            }

            for (var e : fields.entrySet()) {
                PdfFormField f = acro.getField(e.getKey());
                if (f != null) { f.setValue(e.getValue() == null ? "" : e.getValue()); }
            }

            setCheck(acro, "Check Box 1", Boolean.TRUE.equals(req.getPayOnce()));
            setCheck(acro, "Check Box 2", Boolean.TRUE.equals(req.getPayMilestone()));

            if (Boolean.TRUE.equals(req.getPayMilestone())) {
                drawMilestones(pdf, acro, font, req.getMilestones());
            }

            acro.flattenFields();
            pdf.close();
            byte[] pdfBytes = baos.toByteArray();

            // BƯỚC 2.3: UPLOAD FILE PDF LÊN S3
            int nextVersion = contractDocumentRepository.findMaxVersion(contractId, ContractDocumentType.FILLED) + 1;
            String key = "contracts/%d/review_v%d.pdf".formatted(contractId, nextVersion);
            String storageUrl = storageService.save(pdfBytes, key);
            log.info("Đã lưu file PDF xem trước vào S3 tại: {}", storageUrl);

            // BƯỚC 2.4: TẠO BẢN GHI `ContractDocument`
            ContractDocument doc = ContractDocument.builder()
                    .contract(contract).type(ContractDocumentType.FILLED).version(nextVersion).storageUrl(storageUrl)
                    .build();
            contractDocumentRepository.save(doc);

            // BƯỚC 5: TẠO TOKEN, CẬP NHẬT HỢP ĐỒNG VÀ GỬI KAFKA
            // 5.1. Tạo token và thời gian hết hạn (ví dụ: 7 ngày)
            String token = UUID.randomUUID().toString();
            LocalDateTime expiryDate = LocalDateTime.now().plusDays(7);

            // 5.2. Cập nhật hợp đồng với token và trạng thái mới
            contract.setReviewToken(token);
            contract.setReviewTokenExpiresAt(expiryDate);
            contract.setStatus(ContractStatus.PENDING_CLIENT_APPROVAL);
            contractRepository.save(contract);
            log.info("Đã cập nhật trạng thái hợp đồng ID: {} thành PENDING_CLIENT_APPROVAL", contractId);

            // 5.3. Gửi sự kiện qua Kafka
            User client = contract.getProject().getClient();
            User producer = contract.getProject().getCreator(); // Sửa lại thành getCreator()

            if (client != null && producer != null) {
                String reviewLink = "http://your-frontend-app.com/contracts/" + contract.getId() + "/review?token=some_secure_token";
                Map<String, Object> emailParams = Map.of(
                        "producerName", producer.getFullName(),
                        "projectName", contract.getProject().getTitle(),
                        "reviewLink", reviewLink
                );

                NotificationEvent event = NotificationEvent.builder()
                        .recipient(client.getEmail())
                        .subject("[PWB] Mời xem trước hợp đồng cho dự án " + contract.getProject().getTitle())
                        .templateCode("contract-review").param(emailParams).build();

                kafkaTemplate.send("notification-delivery", event);
                log.info("Đã gửi sự kiện email review hợp đồng lên Kafka cho client: {}", client.getEmail());
            }

            // BƯỚC 2.6: CẬP NHẬT TRẠNG THÁI HỢP ĐỒNG
            contract.setStatus(ContractStatus.PENDING_CLIENT_APPROVAL);
            contractRepository.save(contract);
            log.info("Đã cập nhật trạng thái hợp đồng ID: {} thành PENDING_CLIENT_APPROVAL", contractId);

            return pdfBytes;

        } catch (Exception ex) {
            log.error("Lỗi khi tạo file PDF cho hợp đồng ID: {}", contractId, ex);
            throw new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION);
        }
    }

    private PdfFont loadUnicodeFont() throws IOException {
        byte[] fontBytes;
        try (InputStream fin = fontResource.getInputStream()) {
            fontBytes = StreamUtils.copyToByteArray(fin);
        }
        FontProgram fp = FontProgramFactory.createFont(fontBytes);
        return PdfFontFactory.createFont(fp, PdfEncodings.IDENTITY_H);
    }

    private void drawMilestones(PdfDocument pdf,
                                PdfAcroForm acro,
                                PdfFont font,
                                List<MilestoneRequest> milestones) {
        if (milestones == null || milestones.isEmpty()) return;

        Rectangle rect;
        PdfPage page;
        PdfFormField frame = acro != null ? acro.getField("MilestonesFrame") : null;
        if (frame != null && !frame.getWidgets().isEmpty() && frame.getWidgets().get(0).getPage() != null) {
            PdfWidgetAnnotation w = frame.getWidgets().get(0);
            rect = w.getRectangle().toRectangle();
            page = w.getPage();
        } else {
            rect = new Rectangle(0.9732f * INCH, 0.4268f * INCH, 6.4629f * INCH, 5.9503f * INCH);
            int pageIndex = Math.min(3, Math.max(1, pdf.getNumberOfPages()));
            page = pdf.getPage(pageIndex);
        }

        DateTimeFormatter df = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        int pageNum = pdf.getPageNumber(page);
        Canvas canvas = new Canvas(page, rect);
        float remaining = rect.getHeight();

        for (int i = 0; i < milestones.size(); i++) {
            MilestoneRequest m = milestones.get(i);

            String title = nvl(m.getTitle(), "Cột mốc " + (i + 1));
            String due   = m.getDueDate() != null ? m.getDueDate().format(df) : "......";
            String amountStr = (m.getAmount() == null) ? "....................." : String.valueOf(m.getAmount());
            String money = "• Số tiền: " + amountStr + " VND";
            String desc  = (m.getDescription() == null || m.getDescription().trim().isEmpty()) ? null : ("• Mô tả: " + m.getDescription());

            // ====== Yêu cầu #3: thêm dòng số lần chỉnh sửa ======
            String revisions = (m.getEditCount() == null) ? null : ("• Số lần chỉnh sửa: " + m.getEditCount());

            com.itextpdf.layout.element.Div probe = buildMilestoneBlock(font, rect.getWidth(), title, due, money, desc, revisions);
            float needed = measureHeight(probe, rect);

            if (needed > remaining) {
                canvas.close();
                PdfPage newPage = pdf.addNewPage(pageNum + 1, new PageSize(page.getPageSize()));
                pageNum += 1;
                page = newPage;
                canvas = new Canvas(page, rect);
                remaining = rect.getHeight();
            }

            com.itextpdf.layout.element.Div real = buildMilestoneBlock(font, rect.getWidth(), title, due, money, desc, revisions);
            canvas.add(real);
            remaining -= needed;
        }

        canvas.close();
    }

    private com.itextpdf.layout.element.Div buildMilestoneBlock(PdfFont font, float width,
                                                                String title, String due,
                                                                String money, String desc,
                                                                String revisions) {
        com.itextpdf.layout.Style base = new com.itextpdf.layout.Style()
                .setFont(font).setFontSize(10.5f);

        com.itextpdf.layout.element.Div block = new com.itextpdf.layout.element.Div()
                .addStyle(base)
                .setWidth(width)
                .setMargin(0).setPadding(0)
                .setMarginBottom(6);

        com.itextpdf.layout.element.Text tTitle = new com.itextpdf.layout.element.Text(title + "  (Đến hạn: " + due + ")")
                .setFont(font).setFontSize(11f)
                .setCharacterSpacing(0f).setWordSpacing(0f);
        Paragraph pTitle = new Paragraph(tTitle)
                .setFont(font).setFontSize(11f)
                .setBold()
                .setMultipliedLeading(1.2f)
                .setMargin(0).setPadding(0);
        block.add(pTitle);

        com.itextpdf.layout.element.Text tMoney = new com.itextpdf.layout.element.Text(money)
                .setFont(font).setFontSize(10.5f)
                .setCharacterSpacing(0f).setWordSpacing(0f);
        Paragraph pMoney = new Paragraph(tMoney)
                .setFont(font).setFontSize(10.5f)
                .setMultipliedLeading(1.15f)
                .setMargin(0).setPadding(0);
        block.add(pMoney);

        if (desc != null) {
            com.itextpdf.layout.element.Text tDesc = new com.itextpdf.layout.element.Text(desc)
                    .setFont(font).setFontSize(10.5f)
                    .setCharacterSpacing(0f).setWordSpacing(0f);
            Paragraph pDesc = new Paragraph(tDesc)
                    .setFont(font).setFontSize(10.5f)
                    .setMultipliedLeading(1.15f)
                    .setMargin(0).setPadding(0);
            block.add(pDesc);
        }

        // ====== dòng số lần chỉnh sửa ======
        if (revisions != null) {
            com.itextpdf.layout.element.Text tRev = new com.itextpdf.layout.element.Text(revisions)
                    .setFont(font).setFontSize(10.5f)
                    .setCharacterSpacing(0f).setWordSpacing(0f);
            Paragraph pRev = new Paragraph(tRev)
                    .setFont(font).setFontSize(10.5f)
                    .setMultipliedLeading(1.15f)
                    .setMargin(0).setPadding(0);
            block.add(pRev);
        }
        return new com.itextpdf.layout.element.Div(); // Placeholder
    }

    private float measureHeight(com.itextpdf.layout.element.Div block, Rectangle rect) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PdfWriter writer = new PdfWriter(baos);
        PdfDocument scratchPdf = new PdfDocument(writer);
        com.itextpdf.layout.Document scratchDoc = new com.itextpdf.layout.Document(scratchPdf);

        try {
            com.itextpdf.layout.renderer.IRenderer r = block.createRendererSubTree();
            r.setParent(scratchDoc.getRenderer());
            com.itextpdf.layout.layout.LayoutArea area =
                    new com.itextpdf.layout.layout.LayoutArea(1, new Rectangle(rect));
            com.itextpdf.layout.layout.LayoutContext ctx =
                    new com.itextpdf.layout.layout.LayoutContext(area);
            com.itextpdf.layout.layout.LayoutResult res = r.layout(ctx);
            if (res == null || res.getOccupiedArea() == null || res.getOccupiedArea().getBBox() == null) {
                return 14f;
            }
            return res.getOccupiedArea().getBBox().getHeight();
        } finally {
            scratchDoc.close();
        }
    }

    private void setCheck(PdfAcroForm acro, String name, boolean checked) {
        PdfFormField f = acro.getField(name);
        if (f == null) {
            log.warn("Checkbox '{}' not found.", name);
            return;
        }
        String[] states = f.getAppearanceStates();
        String on = Arrays.stream(states).filter(s -> !"Off".equalsIgnoreCase(s))
                .findFirst().orElse("Yes");
        f.setValue(checked ? on : "Off");
    }

    private Map<String, String> mapDtoToPdfFields(ContractPdfFillRequest req) {
        DateTimeFormatter d = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        Map<String, String> m = new LinkedHashMap<>();

        m.put("Text Box 1", ns(req.getContractNo()));
        m.put("Text Box 2", req.getSignDate() != null ? req.getSignDate().format(d) : "");
        m.put("Text Box 3", ns(req.getSignPlace()));

        m.put("Text Box 4", ns(req.getAName()));
        m.put("Text Box 5", ns(req.getACccd()));
        m.put("Text Box 6", req.getACccdIssueDate() != null ? req.getACccdIssueDate().format(d) : "");
        m.put("Text Box 7", ns(req.getACccdIssuePlace()));
        m.put("Text Box 8", ns(req.getAAddress()));
        m.put("Text Box 9", ns(req.getAPhone()));
        m.put("Text Box 10", ns(req.getARepresentative()));
        m.put("Text Box 11", ns(req.getATitle()));
        m.put("Text Box 12", ns(req.getAPoANo()));

        m.put("Text Box 13", ns(req.getBName()));
        m.put("Text Box 14", ns(req.getBCccd()));
        m.put("Text Box 15", req.getBCccdIssueDate() != null ? req.getBCccdIssueDate().format(d) : "");
        m.put("Text Box 16", ns(req.getBCccdIssuePlace()));
        m.put("Text Box 17", ns(req.getBAddress()));
        m.put("Text Box 18", ns(req.getBPhone()));
        m.put("Text Box 19", ns(req.getBRepresentative()));
        m.put("Text Box 20", ns(req.getBTitle()));
        m.put("Text Box 21", ns(req.getBPoANo()));

        m.put("Text Box 22", ns(req.getLine1Item()));
        m.put("Text Box 23", ns(req.getLine1Unit()));
        m.put("Text Box 24", req.getLine1Qty() == null ? "" : String.valueOf(req.getLine1Qty()));
        m.put("Text Box 25", ns(req.getLine1Price()));
        m.put("Text Box 26", ns(req.getLine1Amount()));

        m.put("Text Box 27", ns(req.getLine2Item()));
        m.put("Text Box 28", ns(req.getLine2Unit()));
        m.put("Text Box 29", req.getLine2Qty() == null ? "" : String.valueOf(req.getLine2Qty()));
        m.put("Text Box 30", ns(req.getLine2Price()));
        m.put("Text Box 31", ns(req.getLine2Amount()));

        m.put("Text Box 32", ns(req.getLine3Item()));
        m.put("Text Box 33", ns(req.getLine3Unit()));
        m.put("Text Box 34", req.getLine3Qty() == null ? "" : String.valueOf(req.getLine3Qty()));
        m.put("Text Box 35", ns(req.getLine3Price()));
        m.put("Text Box 36", ns(req.getLine3Amount()));

        return m;
    }

    private void validateMilestonesAmount(List<MilestoneRequest> milestones, BigDecimal grandTotal) {
        if (milestones == null || milestones.isEmpty()) throw new AppException(ErrorCode.BAD_REQUEST);

        BigDecimal remaining = grandTotal;
        for (int i = 0; i < milestones.size(); i++) {
            MilestoneRequest m = milestones.get(i);
            if (m.getTitle() == null || m.getTitle().isBlank()
                    || m.getAmount() == null
                    || m.getDueDate() == null) {
                throw new AppException(ErrorCode.BAD_REQUEST);
            }

            // ====== Yêu cầu #3: bắt buộc nhập editCount khi thanh toán milestone ======
            if (m.getEditCount() == null || m.getEditCount() < 0) {
                throw new AppException(ErrorCode.BAD_REQUEST);
            }

            BigDecimal amt = m.getAmount();
            if (amt.signum() <= 0) throw new AppException(ErrorCode.BAD_REQUEST);
            if (amt.compareTo(remaining) > 0) {
                log.warn("Milestone #{} amount {} exceeds remaining {}", (i + 1), amt, remaining);
                throw new AppException(ErrorCode.BAD_REQUEST);
            }
            remaining = remaining.subtract(amt);
        }
    }

    private record MoneyTotals(BigDecimal sum, BigDecimal vat, BigDecimal grandTotal) {}

    private MoneyTotals computeTotals(ContractPdfFillRequest req) {
        BigDecimal sum = BigDecimal.ZERO;
        sum = sum.add(parseMoney(req.getLine1Amount()));
        sum = sum.add(parseMoney(req.getLine2Amount()));
        sum = sum.add(parseMoney(req.getLine3Amount()));

        BigDecimal vat = sum.multiply(DEFAULT_VAT_RATE).setScale(0, RoundingMode.HALF_UP);
        BigDecimal total = sum.add(vat);
        return new MoneyTotals(sum, vat, total);
    }

    private BigDecimal parseMoney(String s) {
        if (s == null || s.isBlank()) return BigDecimal.ZERO;
        String digits = s.replaceAll("[^0-9-]", "");
        if (digits.isBlank()) return BigDecimal.ZERO;
        return new BigDecimal(digits);
    }

    private String formatMoney(BigDecimal v) {
        NumberFormat nf = NumberFormat.getInstance(Locale.US);
        nf.setMaximumFractionDigits(0);
        nf.setMinimumFractionDigits(0);
        return nf.format(v);
    }

    private String ns(Object o) { return o == null ? "" : String.valueOf(o); }
    private boolean isBlank(String s) { return s == null || s.isBlank(); }
    private String nvl(String s, String def) { return isBlank(s) ? def : s; }
}
