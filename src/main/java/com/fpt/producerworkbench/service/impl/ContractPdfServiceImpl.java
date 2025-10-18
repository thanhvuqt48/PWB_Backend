package com.fpt.producerworkbench.service.impl;

import com.fpt.producerworkbench.common.ContractDocumentType;
import com.fpt.producerworkbench.common.ContractStatus;
import com.fpt.producerworkbench.common.MilestoneStatus;
import com.fpt.producerworkbench.common.PaymentType;
import com.fpt.producerworkbench.dto.request.ContractPdfFillRequest;
import com.fpt.producerworkbench.dto.request.MilestoneRequest;
import com.fpt.producerworkbench.entity.Contract;
import com.fpt.producerworkbench.entity.ContractDocument;
import com.fpt.producerworkbench.entity.Milestone;
import com.fpt.producerworkbench.entity.Project;
import com.fpt.producerworkbench.exception.AppException;
import com.fpt.producerworkbench.exception.ErrorCode;
import com.fpt.producerworkbench.repository.ContractDocumentRepository;
import com.fpt.producerworkbench.repository.ContractRepository;
import com.fpt.producerworkbench.repository.MilestoneRepository;
import com.fpt.producerworkbench.repository.ProjectRepository;
import com.fpt.producerworkbench.service.ContractPdfService;
import com.fpt.producerworkbench.service.FileStorageService;
import com.fpt.producerworkbench.service.FileKeyGenerator;
import com.fpt.producerworkbench.service.ContractPermissionService;
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
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class ContractPdfServiceImpl implements ContractPdfService {

    static final BigDecimal DEFAULT_VAT_RATE = new BigDecimal("0.08");
    static final float INCH = 72f;

    ProjectRepository projectRepository;
    ContractRepository contractRepository;
    MilestoneRepository milestoneRepository;
    ContractDocumentRepository contractDocumentRepository;
    FileStorageService fileStorageService;
    FileKeyGenerator fileKeyGenerator;
    ContractPermissionService contractPermissionService;
    Resource templateResource;
    Resource fontResource;

    public ContractPdfServiceImpl(
            ProjectRepository projectRepository,
            ContractRepository contractRepository,
            ContractDocumentRepository contractDocumentRepository,
            FileStorageService fileStorageService,
            FileKeyGenerator fileKeyGenerator,
            ContractPermissionService contractPermissionService,
            MilestoneRepository milestoneRepository,
            @Value("${pwb.contract.template}") Resource templateResource,
            @Value("${pwb.contract.font}") Resource fontResource
    ) {
        this.projectRepository = projectRepository;
        this.contractRepository = contractRepository;
        this.contractDocumentRepository = contractDocumentRepository;
        this.fileStorageService = fileStorageService;
        this.fileKeyGenerator = fileKeyGenerator;
        this.contractPermissionService = contractPermissionService;
        this.milestoneRepository = milestoneRepository;
        this.templateResource = templateResource;
        this.fontResource = fontResource;
    }

    @Override
    public byte[] fillTemplate(Authentication auth, Long projectId, ContractPdfFillRequest req) {
        if (req.getPercent() == null || req.getPercent().isBlank()) throw new AppException(ErrorCode.BAD_REQUEST);

        var permissions = contractPermissionService.checkContractPermissions(auth, projectId);
        if (!permissions.isCanCreateContract()) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }

        MoneyTotals totals = computeTotals(req);
        String sumFmt = formatMoney(totals.sum);
        String vatFmt = formatMoney(totals.vat);
        String gtFmt  = formatMoney(totals.grandTotal);

        boolean payOnce      = Boolean.TRUE.equals(req.getPayOnce());
        boolean payMilestone = Boolean.TRUE.equals(req.getPayMilestone());
        if (payOnce == payMilestone) throw new AppException(ErrorCode.BAD_REQUEST);

        if (payOnce) {
            if (req.getFpEditAmount() == null) throw new AppException(ErrorCode.BAD_REQUEST);
        }
        if (payMilestone) {
            validateMilestonesAmount(req.getMilestones(), totals.grandTotal);
        }

        if (projectId == null) throw new AppException(ErrorCode.BAD_REQUEST);
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new AppException(ErrorCode.BAD_REQUEST));
        Contract contract = contractRepository.findByProjectId(project.getId()).orElse(null);
        if (contract != null) {
            if (ContractStatus.COMPLETED.equals(contract.getStatus())) {
                throw new AppException(ErrorCode.ALREADY_SIGNED_FINAL);
            }
        } else {
            contract = new Contract();
            contract.setProject(project);
        }
        contract.setContractDetails("Sinh hợp đồng từ PDF fill: " + Optional.ofNullable(req.getContractNo()).orElse(""));
        contract.setTotalAmount(parseMoney(req.getLine1Amount())
                .add(parseMoney(req.getLine2Amount()))
                .add(parseMoney(req.getLine3Amount())));
        contract.setPaymentType(payOnce ? PaymentType.FULL : PaymentType.MILESTONE);
        contract.setStatus(ContractStatus.DRAFT);

        contract.setFpEditAmount(req.getFpEditAmount());

        contract = contractRepository.save(contract);

        if (payMilestone && req.getMilestones() != null) {
            int idx = 1;
            for (MilestoneRequest m : req.getMilestones()) {
                Milestone ms = Milestone.builder()
                        .contract(contract)
                        .title(Optional.ofNullable(m.getTitle()).orElse("Cột mốc " + idx))
                        .description(m.getDescription())
                        .amount(parseMoney(m.getAmount()))
                        .dueDate(m.getDueDate())
                        .status(MilestoneStatus.PENDING)
                        .sequence(idx)
                        .editCount(m.getEditCount())
                        .build();
                milestoneRepository.save(ms);
                idx++;
            }
        }

        try (InputStream in = templateResource.getInputStream();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            PdfReader reader = new PdfReader(in);
            PdfWriter writer = new PdfWriter(baos);
            PdfDocument pdf = new PdfDocument(reader, writer);

            PdfFont font = loadUnicodeFont();
            log.info("Loaded font for iText: {}", fontResource.getFilename());

            PdfAcroForm acro = PdfAcroForm.getAcroForm(pdf, true);
            acro.setGenerateAppearance(true);
            for (PdfFormField f : acro.getFormFields().values()) {
                f.setFont(font).setFontSize(10f);
            }

            Map<String, String> fields = mapDtoToPdfFields(req);
            fields.put("Text Box 37", sumFmt);
            fields.put("Text Box 38", vatFmt);
            fields.put("Text Box 39", gtFmt);
            if (payOnce) fields.put("Text Box 42", gtFmt);

            fields.put("Percent", ns(req.getPercent()));

            if (req.getFpEditAmount() != null) {
                fields.put("FP Edit Amount", String.valueOf(req.getFpEditAmount()));
            } else {
                fields.put("FP Edit Amount", "");
            }

            for (var e : fields.entrySet()) {
                PdfFormField f = acro.getField(e.getKey());
                if (f == null) {
                    log.warn("Field '{}' not found, skip", e.getKey());
                    continue;
                }
                f.setValue(e.getValue() == null ? "" : e.getValue());
            }

            setCheck(acro, "Check Box 1", payOnce);
            setCheck(acro, "Check Box 2", payMilestone);

            if (payMilestone) {
                drawMilestones(pdf, acro, font, req.getMilestones());
            }

            acro.flattenFields();
            pdf.close();

            byte[] out = baos.toByteArray();

            int nextVersion = contractDocumentRepository.findMaxVersion(contract.getId(), ContractDocumentType.FILLED) + 1;
            String fileName = "filled_v" + nextVersion + ".pdf";
            String key = fileKeyGenerator.generateContractDocumentKey(contract.getId(), fileName);
            String url = fileStorageService.uploadBytes(out, key, "application/pdf");

            ContractDocument doc = ContractDocument.builder()
                    .contract(contract)
                    .type(ContractDocumentType.FILLED)
                    .version(nextVersion)
                    .storageUrl(url)
                    .build();
            contractDocumentRepository.save(doc);

            return out;

        } catch (IOException ex) {
            log.error("iText fill error", ex);
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

        return block;
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
                    || m.getAmount() == null || m.getAmount().isBlank()
                    || m.getDueDate() == null) {
                throw new AppException(ErrorCode.BAD_REQUEST);
            }

            if (m.getEditCount() == null || m.getEditCount() < 0) {
                throw new AppException(ErrorCode.BAD_REQUEST);
            }

            BigDecimal amt = parseMoney(m.getAmount());
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
