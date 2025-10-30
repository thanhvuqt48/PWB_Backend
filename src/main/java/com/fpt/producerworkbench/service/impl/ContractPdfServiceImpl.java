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
import com.fpt.producerworkbench.service.FileKeyGenerator;
import com.fpt.producerworkbench.service.FileStorageService;
import com.fpt.producerworkbench.service.ContractPermissionService;
import com.itextpdf.forms.PdfAcroForm;
import com.itextpdf.forms.fields.PdfFormField;
import com.itextpdf.forms.fields.PdfTextFormField;
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
import com.itextpdf.layout.element.Text;
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
import java.lang.reflect.Method;
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
    static final BigDecimal ONE = BigDecimal.ONE;

    static final String[][] TABLE_FIELDS = new String[][]{
            {"Text Box 22","Text Box 23","Text Box 24","Text Box 25","Text Box 26"},
            {"Text Box 27","Text Box 28","Text Box 29","Text Box 30","Text Box 31"},
            {"Text Box 32","Text Box 33","Text Box 34","Text Box 35","Text Box 36"},
            {"Text Box 32,1","Text Box 33,1","Text Box 34,1","Text Box 35,1","Text Box 36,1"},
            {"Text Box 32,2","Text Box 33,2","Text Box 34,2","Text Box 35,2","Text Box 36,2"},
            {"Text Box 32,3","Text Box 33,3","Text Box 34,3","Text Box 35,3","Text Box 36,3"},
            {"Text Box 32,4","Text Box 33,4","Text Box 34,4","Text Box 35,4","Text Box 36,4"},
            {"Text Box 32,5","Text Box 33,5","Text Box 34,5","Text Box 35,5","Text Box 36,5"},
            {"Text Box 32,6","Text Box 33,6","Text Box 34,6","Text Box 35,6","Text Box 36,6"},
            {"Text Box 32,7","Text Box 33,7","Text Box 34,7","Text Box 35,7","Text Box 36,7"}
    };

    static Rectangle inchRect(float left, float bottom, float width, float height) {
        return new Rectangle(left * INCH, bottom * INCH, width * INCH, height * INCH);
    }

    static final Rectangle MS_FIRST_FALLBACK_RECT    = inchRect(0.9732f, 0.4268f, 6.4629f, 3.7365f);
    static final Rectangle TERMS_FIRST_FALLBACK_RECT = inchRect(0.9732f, 0.4268f, 6.4629f, 2.0000f);
    static final Rectangle MS_CONT_RECT              = inchRect(0.9732f, 2.4000f, 6.4629f, 7.8000f);
    static final Rectangle TERMS_CONT_RECT           = inchRect(0.9732f, 2.4000f, 6.4629f, 7.8000f);
    private record PageAndRect(PdfPage page, Rectangle rect) {}
    private void ensureHasAtLeastPages(PdfDocument pdf, int desiredPage) {
        int numPages = pdf.getNumberOfPages();
        if (numPages < desiredPage) {
            PageSize baseSize = (numPages >= 1) ? new PageSize(pdf.getPage(1).getPageSize()) : PageSize.A4;
            for (int i = numPages; i < desiredPage; i++) pdf.addNewPage(baseSize);
        }
    }
    private PageAndRect resolveMilestoneFirstArea(PdfDocument pdf, PdfAcroForm acro) {
        PdfFormField frame = (acro != null) ? acro.getField("MilestonesFrame") : null;
        if (frame != null && !frame.getWidgets().isEmpty() && frame.getWidgets().get(0).getPage() != null) {
            PdfWidgetAnnotation w = frame.getWidgets().get(0);
            return new PageAndRect(w.getPage(), w.getRectangle().toRectangle());
        }
        int desiredPage = 3;
        ensureHasAtLeastPages(pdf, desiredPage);
        return new PageAndRect(pdf.getPage(desiredPage), MS_FIRST_FALLBACK_RECT);
    }

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
        if (!permissions.isCanCreateContract()) throw new AppException(ErrorCode.ACCESS_DENIED);

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
            validateMilestonesAmountPreVat(req.getMilestones(), totals.sum);
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

        contract.setTotalAmount(totals.grandTotal);
        contract.setPaymentType(payOnce ? PaymentType.FULL : PaymentType.MILESTONE);
        contract.setStatus(ContractStatus.DRAFT);
        contract.setFpEditAmount(req.getFpEditAmount());
        contract = contractRepository.save(contract);

        List<BigDecimal> milestoneGrossList = payMilestone
                ? computeMilestoneGrossDistributed(req.getMilestones(), DEFAULT_VAT_RATE, totals.grandTotal)
                : Collections.emptyList();

        if (payMilestone && req.getMilestones() != null) {
            int idx = 0;
            for (MilestoneRequest m : req.getMilestones()) {
                BigDecimal gross = milestoneGrossList.get(idx);
                Milestone ms = Milestone.builder()
                        .contract(contract)
                        .title(Optional.ofNullable(m.getTitle()).orElse("Cột mốc " + (idx + 1)))
                        .description(m.getDescription())
                        .amount(gross)
                        .dueDate(m.getDueDate())
                        .status(MilestoneStatus.PENDING)
                        .sequence(idx + 1)
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
            fields.put("FP Edit Amount", req.getFpEditAmount() == null ? "" : String.valueOf(req.getFpEditAmount()));

            Rectangle addRect = null;
            PdfPage addPage = null;
            PdfFormField addField = acro.getField("additional terms");
            if (addField != null && !addField.getWidgets().isEmpty() && addField.getWidgets().get(0).getPage() != null) {
                PdfWidgetAnnotation w = addField.getWidgets().get(0);
                addRect = w.getRectangle().toRectangle();
                addPage = w.getPage();
                if (addField instanceof PdfTextFormField tf) {
                    tf.setMultiline(true);
                }
                fields.put("additional terms", "");
            } else {
                addRect = TERMS_FIRST_FALLBACK_RECT;
                int pageIndex = Math.min(3, Math.max(1, pdf.getNumberOfPages()));
                addPage = pdf.getPage(pageIndex);
                fields.put("additional terms", "");
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
                drawMilestones(pdf, acro, font, req.getMilestones(), milestoneGrossList);
            }

            acro.flattenFields();

            if (addRect != null && addPage != null) {
                drawAdditionalTermsBlock(addPage, addRect, font, req.getAdditionalTerms());
            }

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
                                List<MilestoneRequest> milestones,
                                List<BigDecimal> milestoneGrossList) {
        if (milestones == null || milestones.isEmpty()) return;

        PageAndRect first = resolveMilestoneFirstArea(pdf, acro);
        PdfPage page = first.page();
        Rectangle rect = first.rect();

        int pageNum = pdf.getPageNumber(page);
        Canvas canvas = new Canvas(page, rect);
        float remaining = rect.getHeight();

        DateTimeFormatter df = DateTimeFormatter.ofPattern("dd/MM/yyyy");

        for (int i = 0; i < milestones.size(); i++) {
            MilestoneRequest m = milestones.get(i);

            String title = nvl(m.getTitle(), "Cột mốc " + (i + 1));
            String due   = m.getDueDate() != null ? m.getDueDate().format(df) : "......";

            String amountStr = (milestoneGrossList != null && milestoneGrossList.size() > i)
                    ? formatMoney(milestoneGrossList.get(i))
                    : ".....................";
            String money = "• Số tiền: " + amountStr + " VND";

            String desc  = (m.getDescription() == null || m.getDescription().trim().isEmpty())
                    ? null : ("• Mô tả: " + m.getDescription());
            String revisions = (m.getEditCount() == null) ? null : ("• Số lần chỉnh sửa: " + m.getEditCount());

            com.itextpdf.layout.element.Div probe =
                    buildMilestoneBlock(font, rect.getWidth(), title, due, money, desc, revisions);
            float needed = measureHeight(probe, rect);

            if (needed > remaining) {
                canvas.close();
                PdfPage newPage = pdf.addNewPage(pageNum + 1, new PageSize(page.getPageSize()));
                pageNum += 1;
                page = newPage;
                rect = MS_CONT_RECT;
                canvas = new Canvas(page, rect);
                remaining = rect.getHeight();
            }

            com.itextpdf.layout.element.Div real =
                    buildMilestoneBlock(font, rect.getWidth(), title, due, money, desc, revisions);
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

    private record Line(String item, String unit, Integer qty, String price) {}

    private List<Line> collectLines(ContractPdfFillRequest r) {
        List<Line> list = new ArrayList<>(10);
        for (int i = 1; i <= 10; i++) {
            String item  = tryGetString(r, "getLine" + i + "Item");
            String unit  = tryGetString(r, "getLine" + i + "Unit");
            Integer qty  = tryGetInteger(r, "getLine" + i + "Qty");
            String price = tryGetString(r, "getLine" + i + "Price");
            list.add(new Line(item, unit, qty, price));
        }
        return list;
    }

    private String tryGetString(Object target, String method) {
        try {
            Method m = target.getClass().getMethod(method);
            Object v = m.invoke(target);
            return v == null ? null : String.valueOf(v);
        } catch (Exception ignore) { return null; }
    }
    private Integer tryGetInteger(Object target, String method) {
        try {
            Method m = target.getClass().getMethod(method);
            Object v = m.invoke(target);
            return (v == null) ? null : (Integer) v;
        } catch (Exception ignore) { return null; }
    }

    private BigDecimal computeAmount(Integer qty, String price) {
        if (qty == null || qty <= 0) return BigDecimal.ZERO;
        BigDecimal p = parseMoney(price);
        return p.multiply(BigDecimal.valueOf(qty.longValue()));
    }

    private Map<String, String> mapDtoToPdfFields(ContractPdfFillRequest req) {
        DateTimeFormatter d = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        Map<String, String> m = new LinkedHashMap<>();

        m.put("Text Box 1",  ns(req.getContractNo()));
        m.put("Text Box 2",  req.getSignDate() != null ? req.getSignDate().format(d) : "");
        m.put("Text Box 3",  ns(req.getSignPlace()));

        m.put("Text Box 4",  ns(req.getAName()));
        m.put("Text Box 5",  ns(req.getACccd()));
        m.put("Text Box 6",  req.getACccdIssueDate() != null ? req.getACccdIssueDate().format(d) : "");
        m.put("Text Box 7",  ns(req.getACccdIssuePlace()));
        m.put("Text Box 8",  ns(req.getAAddress()));
        m.put("Text Box 9",  ns(req.getAPhone()));
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

        List<Line> lines = collectLines(req);
        for (int i = 0; i < TABLE_FIELDS.length; i++) {
            String[] f = TABLE_FIELDS[i];
            Line ln = (i < lines.size()) ? lines.get(i) : null;
            if (ln == null) ln = new Line(null, null, null, null);

            m.put(f[0], ns(ln.item));
            m.put(f[1], ns(ln.unit));
            m.put(f[2], ln.qty == null ? "" : String.valueOf(ln.qty));
            m.put(f[3], ns(ln.price));

            BigDecimal amt = computeAmount(ln.qty, ln.price);
            boolean filled = (ln.qty != null && ln.qty > 0 && !isBlank(ln.price));
            m.put(f[4], filled ? formatMoney(amt) : "");
        }

        m.put("additional terms", "");
        return m;
    }

    private record MoneyTotals(BigDecimal sum, BigDecimal vat, BigDecimal grandTotal) {}

    private MoneyTotals computeTotals(ContractPdfFillRequest req) {
        BigDecimal sum = BigDecimal.ZERO;
        for (Line ln : collectLines(req)) {
            sum = sum.add(computeAmount(ln.qty, ln.price));
        }
        BigDecimal vat = sum.multiply(DEFAULT_VAT_RATE).setScale(0, RoundingMode.HALF_UP);
        BigDecimal total = sum.add(vat);
        return new MoneyTotals(sum, vat, total);
    }

    private void validateMilestonesAmountPreVat(List<MilestoneRequest> milestones, BigDecimal preVatSum) {
        if (milestones == null || milestones.isEmpty()) throw new AppException(ErrorCode.BAD_REQUEST);

        BigDecimal runningPreVat = BigDecimal.ZERO;

        for (MilestoneRequest m : milestones) {
            if (m.getTitle() == null || m.getTitle().isBlank()
                    || m.getAmount() == null || m.getAmount().isBlank()
                    || m.getDueDate() == null) {
                throw new AppException(ErrorCode.BAD_REQUEST);
            }
            if (m.getEditCount() == null || m.getEditCount() < 0) {
                throw new AppException(ErrorCode.BAD_REQUEST);
            }

            BigDecimal amtPreVat = parseMoney(m.getAmount());
            if (amtPreVat.signum() <= 0) throw new AppException(ErrorCode.BAD_REQUEST);

            runningPreVat = runningPreVat.add(amtPreVat);
            if (runningPreVat.compareTo(preVatSum) > 0) {
                throw new AppException(ErrorCode.MILESTONES_TOTAL_EXCEEDS);
            }
        }

        if (runningPreVat.compareTo(preVatSum) < 0) {
            throw new AppException(ErrorCode.MILESTONES_TOTAL_NOT_ENOUGH);
        }
    }

    private List<BigDecimal> computeMilestoneGrossDistributed(List<MilestoneRequest> milestones,
                                                              BigDecimal vatRate,
                                                              BigDecimal expectedGrandTotal) {
        if (milestones == null || milestones.isEmpty()) return Collections.emptyList();

        List<BigDecimal> grossRounded = new ArrayList<>(milestones.size());
        BigDecimal factor = ONE.add(vatRate);

        for (MilestoneRequest m : milestones) {
            BigDecimal preVat = parseMoney(m.getAmount());
            BigDecimal grossExact = preVat.multiply(factor);
            BigDecimal gross = grossExact.setScale(0, RoundingMode.HALF_UP);
            grossRounded.add(gross);
        }

        BigDecimal sumGross = grossRounded.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal delta = expectedGrandTotal.subtract(sumGross);

        if (delta.signum() != 0) {
            int idxLargest = 0;
            for (int i = 1; i < grossRounded.size(); i++) {
                if (grossRounded.get(i).compareTo(grossRounded.get(idxLargest)) > 0) {
                    idxLargest = i;
                }
            }
            BigDecimal adjusted = grossRounded.get(idxLargest).add(delta);
            if (adjusted.signum() <= 0) {
                int last = grossRounded.size() - 1;
                adjusted = grossRounded.get(last).add(delta);
                if (adjusted.signum() <= 0) {
                    throw new AppException(ErrorCode.BAD_REQUEST);
                }
                grossRounded.set(last, adjusted);
            } else {
                grossRounded.set(idxLargest, adjusted);
            }
        }

        BigDecimal finalSum = grossRounded.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if (finalSum.compareTo(expectedGrandTotal) != 0) {
            throw new AppException(ErrorCode.BAD_REQUEST);
        }
        return grossRounded;
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

    private void drawAdditionalTermsBlock(PdfPage startPage, Rectangle rectFirst, PdfFont font, String raw) {
        PdfDocument pdf = startPage.getDocument();

        PdfPage page = startPage;
        Rectangle rect = rectFirst;
        int pageNum = pdf.getPageNumber(page);
        Canvas canvas = new Canvas(page, rect);

        float remaining = rect.getHeight();

        Paragraph heading = new Paragraph(new Text("Bổ sung điều khoản").setBold())
                .setFont(font).setFontSize(11.5f)
                .setMargin(0).setPadding(0)
                .setMultipliedLeading(1.25f);

        com.itextpdf.layout.element.Div headDiv = new com.itextpdf.layout.element.Div()
                .setWidth(rect.getWidth()).setMargin(0).setPadding(0)
                .add(heading);
        float need = measureHeight(headDiv, rect);

        if (need > remaining) {
            canvas.close();
            PdfPage newPage = pdf.addNewPage(pageNum + 1, new PageSize(page.getPageSize()));
            pageNum += 1;
            page = newPage;
            rect = TERMS_CONT_RECT;
            canvas = new Canvas(page, rect);
            remaining = rect.getHeight();
        }
        canvas.add(heading);
        remaining -= need;

        java.util.List<String> clauses = splitClauses(raw);

        if (clauses.isEmpty()) {
            Paragraph p = new Paragraph()
                    .setFont(font).setFontSize(11f)
                    .setMultipliedLeading(1.2f)
                    .setMarginTop(2).setMarginBottom(0)
                    .setMarginLeft(0).setMarginRight(0);
            p.add(new Text("Điều 9. ").setBold());

            com.itextpdf.layout.element.Div div = new com.itextpdf.layout.element.Div()
                    .setWidth(rect.getWidth()).setMargin(0).setPadding(0)
                    .add(p);
            need = measureHeight(div, rect);

            if (need > remaining) {
                canvas.close();
                PdfPage newPage = pdf.addNewPage(pageNum + 1, new PageSize(page.getPageSize()));
                pageNum += 1;
                page = newPage;
                rect = TERMS_CONT_RECT;
                canvas = new Canvas(page, rect);
                remaining = rect.getHeight();
            }
            canvas.add(p);
            canvas.close();
            return;
        }

        int base = 10;
        for (int i = 0; i < clauses.size(); i++) {
            String body = clauses.get(i);
            int no = base + i;

            Paragraph p = new Paragraph()
                    .setFont(font).setFontSize(11f)
                    .setMultipliedLeading(1.2f)
                    .setMarginTop(i == 0 ? 2 : 0).setMarginBottom(0)
                    .setMarginLeft(0).setMarginRight(0);
            p.add(new Text("Điều " + no + ". ").setBold());
            p.add(new Text(body));

            com.itextpdf.layout.element.Div div = new com.itextpdf.layout.element.Div()
                    .setWidth(rect.getWidth()).setMargin(0).setPadding(0)
                    .add(p);
            need = measureHeight(div, rect);

            if (need > remaining) {
                canvas.close();
                PdfPage newPage = pdf.addNewPage(pageNum + 1, new PageSize(page.getPageSize()));
                pageNum += 1;
                page = newPage;
                rect = TERMS_CONT_RECT;
                canvas = new Canvas(page, rect);
                remaining = rect.getHeight();
            }

            canvas.add(p);
            remaining -= need;
        }

        canvas.close();
    }

    private List<String> splitClauses(String raw) {
        List<String> out = new ArrayList<>();
        if (raw == null) return out;

        String txt = raw.replace("\r\n", "\n").trim();
        if (txt.isBlank()) return out;

        String[] parts = txt.split("\\n\\s*\\n");
        for (String p : parts) {
            String s = p.trim();
            s = s.replaceFirst("(?iu)^điều\\s*\\d+\\s*\\.?\\s*", "");
            if (!s.isBlank()) out.add(s);
        }
        return out;
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
