//gốc
//package com.fpt.producerworkbench.service.impl;
//
//import com.fpt.producerworkbench.dto.request.ContractPdfFillRequest;
//import com.fpt.producerworkbench.dto.request.MilestoneReq;
//import com.fpt.producerworkbench.entity.User;
//import com.fpt.producerworkbench.exception.AppException;
//import com.fpt.producerworkbench.exception.ErrorCode;
//import com.fpt.producerworkbench.repository.UserRepository;
//import com.fpt.producerworkbench.service.ContractPdfService;
//import lombok.AccessLevel;
//import lombok.experimental.FieldDefaults;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.core.io.Resource;
//import org.springframework.security.core.Authentication;
//import org.springframework.security.core.userdetails.UserDetails;
//import org.springframework.security.oauth2.jwt.Jwt;
//import org.springframework.stereotype.Service;
//import org.springframework.util.StreamUtils;
//
//import java.io.ByteArrayOutputStream;
//import java.io.IOException;
//import java.io.InputStream;
//import java.time.format.DateTimeFormatter;
//import java.util.*;
//
//import com.itextpdf.io.font.FontProgram;
//import com.itextpdf.io.font.FontProgramFactory;
//import com.itextpdf.io.font.PdfEncodings;
//import com.itextpdf.kernel.font.PdfFont;
//import com.itextpdf.kernel.font.PdfFontFactory;
//import com.itextpdf.kernel.geom.Rectangle;
//import com.itextpdf.kernel.pdf.*;
//import com.itextpdf.kernel.pdf.annot.PdfWidgetAnnotation;
//import com.itextpdf.forms.PdfAcroForm;
//import com.itextpdf.forms.fields.PdfFormField;
//import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
//import com.itextpdf.layout.Canvas;
//import com.itextpdf.layout.element.ListItem;
//import com.itextpdf.layout.element.Paragraph;
//
//@Service
//@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
//@Slf4j
//public class ContractPdfServiceImpl implements ContractPdfService {
//
//    UserRepository userRepository;
//    Resource templateResource;   // PDF mẫu có field placeholder "MilestonesFrame"
//    Resource fontResource;       // Khuyên dùng: NotoSerif-Regular.ttf hoặc NotoSans-Regular.ttf
//
//    public ContractPdfServiceImpl(
//            UserRepository userRepository,
//            @Value("${pwb.contract.template}") Resource templateResource,
//            @Value("${pwb.contract.font}") Resource fontResource
//    ) {
//        this.userRepository = userRepository;
//        this.templateResource = templateResource;
//        this.fontResource = fontResource;
//    }
//
//    @Override
//    public byte[] fillTemplate(Authentication auth, ContractPdfFillRequest req) {
//        // (Nếu cần) resolveCurrentUserId(auth);  // giữ nguyên logic của bạn
//        try (InputStream in = templateResource.getInputStream();
//             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
//
//            PdfReader reader = new PdfReader(in);
//            PdfWriter writer = new PdfWriter(baos);
//            PdfDocument pdf = new PdfDocument(reader, writer);
//
//            // 1) Font Unicode (hỗ trợ tiếng Việt)
//            PdfFont font = loadUnicodeFont();
//            log.info("Loaded font for iText: {}", fontResource.getFilename());
//
//            // 2) Lấy và cấu hình AcroForm
//            PdfAcroForm acro = PdfAcroForm.getAcroForm(pdf, true);
//            acro.setGenerateAppearance(true);
//
//            // Áp font + size cho TẤT CẢ field (để renderer tạo appearance đúng)
//            for (PdfFormField f : acro.getFormFields().values()) {
//                f.setFont(font).setFontSize(10f);
//            }
//
//            // 3) Điền dữ liệu vào fields
//            Map<String, String> fields = mapDtoToPdfFields(req);
//            for (var e : fields.entrySet()) {
//                PdfFormField f = acro.getField(e.getKey());
//                if (f == null) {
//                    log.warn("Field '{}' not found, skip", e.getKey());
//                    continue;
//                }
//                f.setValue(e.getValue() == null ? "" : e.getValue());
//            }
//
//            // Checkbox
//            setCheck(acro, "Check Box 1", Boolean.TRUE.equals(req.getPayOnce()));
//            setCheck(acro, "Check Box 2", Boolean.TRUE.equals(req.getPayMilestone()));
//
//            // 4) Vẽ Milestones động (iText Layout) vào khung placeholder
//            java.util.List<MilestoneReq> milestones = resolveMilestones(req);
//            drawMilestones(pdf, acro, font, milestones);
//
//            // 5) Flatten form
//            acro.flattenFields();
//            pdf.close();
//            return baos.toByteArray();
//
//        } catch (IOException ex) {
//            log.error("iText fill error", ex);
//            throw new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION);
//        }
//    }
//
//    // ===== Helpers =====
//
//    /** Font Unicode: dùng overload tương thích nhiều version */
//    private PdfFont loadUnicodeFont() throws IOException {
//        byte[] fontBytes;
//        try (InputStream fin = fontResource.getInputStream()) {
//            fontBytes = StreamUtils.copyToByteArray(fin);
//        }
//        FontProgram fp = FontProgramFactory.createFont(fontBytes);
//        // Nhiều bản iText 7 chỉ có (FontProgram, String)
//        // Embedding là mặc định khi dùng FontProgram
//        return PdfFontFactory.createFont(fp, PdfEncodings.IDENTITY_H);
//    }
//
//    /** Vẽ Milestones động */
//    private void drawMilestones(PdfDocument pdf,
//                                PdfAcroForm acro,
//                                PdfFont font,
//                                java.util.List<MilestoneReq> milestones) {
//
//        if (milestones == null || milestones.isEmpty()) {
//            log.info("No milestones to render.");
//            return;
//        }
//
//        Rectangle rect;
//        PdfPage page;
//
//        PdfFormField frame = acro.getField("MilestonesFrame"); // hoặc "MILESTONE_BOX" tùy bạn đặt tên
//        if (frame != null && !frame.getWidgets().isEmpty()) {
//            PdfWidgetAnnotation w = frame.getWidgets().get(0);
//            rect = w.getRectangle().toRectangle();
//            page = w.getPage();
//        } else {
//            // ==== Fallback: dùng số đo trong ảnh (Units: Inches) ====
//
//            final float INCH = 72f;
//            float leftIn   = 0.9732f;
//            float bottomIn = 0.4268f;
//            float widthIn  = 6.4629f;
//            float heightIn = 5.9503f;
//
//            float x = leftIn   * INCH;
//            float y = bottomIn * INCH;
//            float w = widthIn  * INCH;
//            float h = heightIn * INCH;
//
//            rect = new Rectangle(x, y, w, h);
//
//
//            page = pdf.getPage(3);
//        }
//
//        PdfCanvas pdfCanvas = new PdfCanvas(page);
//        // Dùng đúng constructor: (PdfCanvas, Rectangle)
//        Canvas canvas = new Canvas(pdfCanvas, rect);
//
//        com.itextpdf.layout.element.List outer =
//                new com.itextpdf.layout.element.List()
//                        .setSymbolIndent(12)
//                        .setFont(font)
//                        .setFontSize(10.5f)
//                        .setListSymbol("\u25A0"); // ■
//
//        for (int i = 0; i < milestones.size(); i++) {
//            var m = milestones.get(i);
//
//            // ListItem KHÔNG có constructor nhận Paragraph ở 7.0/7.1
//            ListItem li = new ListItem();
//            li.add(new Paragraph("Cột mốc " + (i + 1) + ":")
//                    .setBold()
//                    .setFont(font)
//                    .setFontSize(11));
//
//            com.itextpdf.layout.element.List inner =
//                    new com.itextpdf.layout.element.List()
//                            .setListSymbol("\u2022") // •
//                            .setSymbolIndent(10)
//                            .setMarginLeft(8)
//                            .setFont(font)
//                            .setFontSize(10.5f);
//
//            String moneyLine = "Số tiền: " + nvl(m.getAmount(), ".....................") + " VND"
//                    + (isBlank(m.getPercent()) ? "" : " (tương đương " + m.getPercent() + "%)");
//
//            ListItem moneyItem = new ListItem();
//            moneyItem.add(new Paragraph(moneyLine).setFont(font));
//            inner.add(moneyItem);
//
//            if (!isBlank(m.getNote())) {
//                ListItem noteItem = new ListItem();
//                noteItem.add(new Paragraph("Quy trình thanh toán: " + m.getNote()).setFont(font));
//                inner.add(noteItem);
//            }
//
//            li.add(inner);
//            outer.add(li);
//        }
//
//        canvas.add(outer);
//        canvas.close();
//    }
//
//    /** Tích / bỏ tích checkbox có thể có export value khác "Yes" */
//    private void setCheck(PdfAcroForm acro, String name, boolean checked) {
//        PdfFormField f = acro.getField(name);
//        if (f == null) {
//            log.warn("Checkbox '{}' not found.", name);
//            return;
//        }
//        String[] states = f.getAppearanceStates();
//        // tìm state != "Off"
//        String on = Arrays.stream(states)
//                .filter(s -> !"Off".equalsIgnoreCase(s))
//                .findFirst()
//                .orElse("Yes");
//        f.setValue(checked ? on : "Off");
//    }
//
//    private java.util.List<MilestoneReq> resolveMilestones(ContractPdfFillRequest req) {
//        if (req.getMilestones() != null && !req.getMilestones().isEmpty()) {
//            return req.getMilestones();
//        }
//        // Tương thích ngược từ các field ms1/ms2/ms3 cũ
//        java.util.List<MilestoneReq> list = new ArrayList<>();
//        if (!isBlank(req.getMs1Amount()) || !isBlank(req.getMs1Percent()))
//            list.add(MilestoneReq.builder().amount(req.getMs1Amount()).percent(req.getMs1Percent()).build());
//        if (!isBlank(req.getMs2Amount()) || !isBlank(req.getMs2Percent()))
//            list.add(MilestoneReq.builder().amount(req.getMs2Amount()).percent(req.getMs2Percent()).build());
//        if (!isBlank(req.getMs3Amount()) || !isBlank(req.getMs3Percent()))
//            list.add(MilestoneReq.builder().amount(req.getMs3Amount()).percent(req.getMs3Percent()).build());
//        return list;
//    }
//
//    private Map<String, String> mapDtoToPdfFields(ContractPdfFillRequest req) {
//        DateTimeFormatter d = DateTimeFormatter.ofPattern("dd/MM/yyyy");
//        Map<String, String> m = new LinkedHashMap<>();
//
//        // Header
//        m.put("Text Box 1",  ns(req.getContractNo()));
//        m.put("Text Box 2",  req.getSignDate() != null ? req.getSignDate().format(d) : "");
//        m.put("Text Box 3",  ns(req.getSignPlace()));
//
//        // Bên A
//        m.put("Text Box 4",  ns(req.getAName()));
//        m.put("Text Box 5",  ns(req.getACccd()));
//        m.put("Text Box 6",  req.getACccdIssueDate() != null ? req.getACccdIssueDate().format(d) : "");
//        m.put("Text Box 7",  ns(req.getACccdIssuePlace()));
//        m.put("Text Box 8",  ns(req.getAAddress()));
//        m.put("Text Box 9",  ns(req.getAPhone()));
//        m.put("Text Box 10", ns(req.getARepresentative()));
//        m.put("Text Box 11", ns(req.getATitle()));
//        m.put("Text Box 12", ns(req.getAPoANo()));
//
//        // Bên B
//        m.put("Text Box 13", ns(req.getBName()));
//        m.put("Text Box 14", ns(req.getBCccd()));
//        m.put("Text Box 15", req.getBCccdIssueDate() != null ? req.getBCccdIssueDate().format(d) : "");
//        m.put("Text Box 16", ns(req.getBCccdIssuePlace()));
//        m.put("Text Box 17", ns(req.getBAddress()));
//        m.put("Text Box 18", ns(req.getBPhone()));
//        m.put("Text Box 19", ns(req.getBRepresentative()));
//        m.put("Text Box 20", ns(req.getBTitle()));
//        m.put("Text Box 21", ns(req.getBPoANo()));
//
//        // Hạng mục
//        m.put("Text Box 22", ns(req.getLine1Item()));
//        m.put("Text Box 23", ns(req.getLine1Unit()));
//        m.put("Text Box 24", req.getLine1Qty() == null ? "" : String.valueOf(req.getLine1Qty()));
//        m.put("Text Box 25", ns(req.getLine1Price()));
//        m.put("Text Box 26", ns(req.getLine1Amount()));
//
//        m.put("Text Box 27", ns(req.getLine2Item()));
//        m.put("Text Box 28", ns(req.getLine2Unit()));
//        m.put("Text Box 29", req.getLine2Qty() == null ? "" : String.valueOf(req.getLine2Qty()));
//        m.put("Text Box 30", ns(req.getLine2Price()));
//        m.put("Text Box 31", ns(req.getLine2Amount()));
//
//        m.put("Text Box 32", ns(req.getLine3Item()));
//        m.put("Text Box 33", ns(req.getLine3Unit()));
//        m.put("Text Box 34", req.getLine3Qty() == null ? "" : String.valueOf(req.getLine3Qty()));
//        m.put("Text Box 35", ns(req.getLine3Price()));
//        m.put("Text Box 36", ns(req.getLine3Amount()));
//
//        // Tổng hợp
//        m.put("Text Box 37", ns(req.getSumAmount()));
//        m.put("Text Box 38", ns(req.getVat8()));
//        m.put("Text Box 39", ns(req.getGrandTotal()));
//        m.put("Text Box 40", ns(req.getContractPriceText()));
//        m.put("Text Box 41", ns(req.getContractPriceInWords()));
//        m.put("Text Box 42", ns(req.getGrandTotal()));
//
//        return m;
//    }
//
//    private String ns(Object o) { return o == null ? "" : String.valueOf(o); }
//    private boolean isBlank(String s) { return s == null || s.isBlank(); }
//    private String nvl(String s, String def) { return isBlank(s) ? def : s; }
//
//    // (Optional) Nếu bạn vẫn cần resolveCurrentUserId như code cũ:
//    @SuppressWarnings("unused")
//    private Long resolveCurrentUserId(Authentication authentication) {
//        if (authentication == null) throw new AppException(ErrorCode.UNAUTHENTICATED);
//        String email = null;
//        Object principal = authentication.getPrincipal();
//        if (principal instanceof Jwt jwt) {
//            email = jwt.getClaimAsString("email");
//            if (email == null || email.isBlank()) email = jwt.getClaimAsString("sub");
//        } else if (principal instanceof UserDetails ud) {
//            email = ud.getUsername();
//        } else if (principal instanceof User u) {
//            email = u.getEmail();
//        }
//        if (email == null || email.isBlank()) email = authentication.getName();
//        if (email == null || email.isBlank()) throw new AppException(ErrorCode.UNAUTHENTICATED);
//        return userRepository.findByEmail(email).map(User::getId)
//                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
//    }
//}


//quan trọng
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
import com.fpt.producerworkbench.repository.ContractRepository;
import com.fpt.producerworkbench.repository.MilestoneRepository;
import com.fpt.producerworkbench.repository.ProjectRepository;
import com.fpt.producerworkbench.repository.UserRepository;
import com.fpt.producerworkbench.service.ContractPdfService;
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

import com.itextpdf.io.font.FontProgram;
import com.itextpdf.io.font.FontProgramFactory;
import com.itextpdf.io.font.PdfEncodings;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.*;
import com.itextpdf.kernel.pdf.annot.PdfWidgetAnnotation;
import com.itextpdf.forms.PdfAcroForm;
import com.itextpdf.forms.fields.PdfFormField;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import com.itextpdf.layout.Canvas;
import com.itextpdf.layout.element.ListItem;
import com.itextpdf.layout.element.Paragraph;

//storage
import com.fpt.producerworkbench.repository.ContractDocumentRepository;
import com.fpt.producerworkbench.storage.StorageService;

@Service
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class ContractPdfServiceImpl implements ContractPdfService {

    static final BigDecimal DEFAULT_VAT_RATE = new BigDecimal("0.08"); // 8%

    UserRepository userRepository;
    ProjectRepository projectRepository;
    ContractRepository contractRepository;
    MilestoneRepository milestoneRepository;

    //storage
    ContractDocumentRepository contractDocumentRepository;
    StorageService storageService;

    Resource templateResource;
    Resource fontResource;

    public ContractPdfServiceImpl(
            UserRepository userRepository,
            ProjectRepository projectRepository,
            ContractRepository contractRepository,
            ContractDocumentRepository contractDocumentRepository,
            StorageService storageService,
            MilestoneRepository milestoneRepository,
            @Value("${pwb.contract.template}") Resource templateResource,
            @Value("${pwb.contract.font}") Resource fontResource
    ) {
        this.userRepository = userRepository;
        this.projectRepository = projectRepository;
        this.contractRepository = contractRepository;
        this.contractDocumentRepository = contractDocumentRepository;
        this.storageService = storageService;
        this.milestoneRepository = milestoneRepository;
        this.templateResource = templateResource;
        this.fontResource = fontResource;
    }

    @Override
    public byte[] fillTemplate(Authentication auth, ContractPdfFillRequest req) {
        // ---- 0) Quyền OWNER ----
//        assertOwner(auth);

        // ---- 1) RÀNG BUỘC NGHIỆP VỤ MỚI ----
        // TÍNH TOÁN sumAmount, vatAmount, grandTotal
        MoneyTotals totals = computeTotals(req);
        String sumFmt  = formatMoney(totals.sum);
        String vatFmt  = formatMoney(totals.vat);
        String gtFmt   = formatMoney(totals.grandTotal);
        // bắt buộc chọn đúng 1 phương thức thanh toán
        boolean payOnce = Boolean.TRUE.equals(req.getPayOnce());
        boolean payMilestone = Boolean.TRUE.equals(req.getPayMilestone());
        if (payOnce == payMilestone) {
            throw new AppException(ErrorCode.BAD_REQUEST);
        }
        if (payMilestone) {
//            if (req.getMilestones() == null || req.getMilestones().isEmpty()) {
//                throw new AppException(ErrorCode.BAD_REQUEST);
//            }
            validateMilestonesAmount(req.getMilestones(), totals.grandTotal);
        }

        // ---- 2.1) LƯU CONTRACT = DRAFT ----
        if (req.getProjectId() == null) throw new AppException(ErrorCode.BAD_REQUEST);
        Project project = projectRepository.findById(req.getProjectId())
                .orElseThrow(() -> new AppException(ErrorCode.BAD_REQUEST));

        Contract contract = new Contract();
        contract.setProject(project);
        contract.setContractDetails("Sinh hợp đồng từ PDF fill: " + Optional.ofNullable(req.getContractNo()).orElse(""));
        contract.setTotalAmount(parseMoney(req.getLine1Amount())
                .add(parseMoney(req.getLine2Amount()))
                .add(parseMoney(req.getLine3Amount()))); // tổng trước VAT (tuỳ nghiệp vụ)
        contract.setPaymentType(Boolean.TRUE.equals(req.getPayOnce())
                ? PaymentType.FULL : PaymentType.MILESTONE); // đổi theo enum của bạn
        contract.setStatus(ContractStatus.DRAFT);

        // Nếu bạn đã áp dụng phiên bản Contract “mở rộng” cho SignNow (signnowStatus,...):
        // contract.setSignnowStatus(ContractStatus.DRAFT);

        contract = contractRepository.save(contract);

        // ---- 2.2) LƯU MILESTONES (nếu payMilestone) ----
        if (Boolean.TRUE.equals(req.getPayMilestone()) && req.getMilestones() != null) {
            int idx = 1;
            for (MilestoneRequest m : req.getMilestones()) {
                Milestone ms = Milestone.builder()
                        .contract(contract)
                        .title(Optional.ofNullable(m.getTitle()).orElse("Cột mốc " + idx))
                        .description(m.getDescription())               // <-- chỉ còn description
                        .amount(parseMoney(m.getAmount()))
                        .dueDate(m.getDueDate())
                        .status(MilestoneStatus.PENDING)
                        .sequence(idx)
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

            // font unicode
            PdfFont font = loadUnicodeFont();
            log.info("Loaded font for iText: {}", fontResource.getFilename());

            // AcroForm
            PdfAcroForm acro = PdfAcroForm.getAcroForm(pdf, true);
            acro.setGenerateAppearance(true);
            for (PdfFormField f : acro.getFormFields().values()) {
                f.setFont(font).setFontSize(10f);
            }

            // ---- 3) Điền các field (không dùng sum/vat/gt từ client) ----
            Map<String, String> fields = mapDtoToPdfFields(req);

            // ghi đè 3 ô tổng hợp bằng kết quả tính toán
            fields.put("Text Box 37", sumFmt);     // sumAmount
            fields.put("Text Box 38", vatFmt);     // VAT tiền (8%)
            fields.put("Text Box 39", gtFmt);      // grandTotal

            // Nếu PayOnce: Text Box 42 bắt buộc & = grandTotal
            if (payOnce) {
                fields.put("Text Box 42", gtFmt);
            }

            for (var e : fields.entrySet()) {
                PdfFormField f = acro.getField(e.getKey());
                if (f == null) {
                    log.warn("Field '{}' not found, skip", e.getKey());
                    continue;
                }
                f.setValue(e.getValue() == null ? "" : e.getValue());
            }

            // checkbox
            setCheck(acro, "Check Box 1", payOnce);
            setCheck(acro, "Check Box 2", payMilestone);

            // ---- 4) Vẽ milestones CHỈ khi payMilestone ----
            if (payMilestone) {
                drawMilestones(pdf, acro, font, req.getMilestones());
            }

            // ---- 5) Flatten ----
            acro.flattenFields();
            pdf.close();

            //storage
            byte[] out = baos.toByteArray();

// === GHI FILE ĐÃ FILL VÀO STORAGE + TẠO CONTRACT_DOCUMENT ===
            int nextVersion = contractDocumentRepository.findMaxVersion(contract.getId(), ContractDocumentType.FILLED) + 1;
            String key = "contracts/%d/filled_v%d.pdf".formatted(contract.getId(), nextVersion);
            String url = storageService.save(out, key);

            ContractDocument doc = ContractDocument.builder()
                    .contract(contract)
                    .type(ContractDocumentType.FILLED)
                    .version(nextVersion)
                    .storageUrl(url)
                    .build();
            contractDocumentRepository.save(doc);

            return out;

            //gốc
//            return baos.toByteArray();

        } catch (IOException ex) {
            log.error("iText fill error", ex);
            throw new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION);
        }
    }

    // ================== Helper: QUYỀN OWNER ==================
//    private void assertOwner(Authentication authentication) {
//        if (authentication == null) throw new AppException(ErrorCode.UNAUTHENTICATED);
//
//        // 1) từ GrantedAuthority
//        for (GrantedAuthority ga : authentication.getAuthorities()) {
//            String a = ga.getAuthority();
//            if ("ROLE_OWNER".equalsIgnoreCase(a) || "OWNER".equalsIgnoreCase(a)) return;
//        }
//        // 2) từ JWT claim (project_role hoặc role)
//        Object p = authentication.getPrincipal();
//        if (p instanceof Jwt jwt) {
//            String r = Optional.ofNullable(jwt.getClaimAsString("project_role"))
//                    .orElse(jwt.getClaimAsString("role"));
//            if (r != null && r.equalsIgnoreCase("OWNER")) return;
//        }
//        // 3) fallback DB (nếu User có field role)
//        if (p instanceof UserDetails ud) {
//            if (ud.getAuthorities().stream().anyMatch(ga ->
//                    "ROLE_OWNER".equalsIgnoreCase(ga.getAuthority()) || "OWNER".equalsIgnoreCase(ga.getAuthority()))) return;
//        } else if (p instanceof User u) {
//            if (u.getRole() != null && u.getRole().name().equalsIgnoreCase("OWNER")) return;
//        }
//        throw new AppException(ErrorCode.BAD_REQUEST);
//    }

    // ================== Helper: FONT ==================
    private PdfFont loadUnicodeFont() throws IOException {
        byte[] fontBytes;
        try (InputStream fin = fontResource.getInputStream()) {
            fontBytes = StreamUtils.copyToByteArray(fin);
        }
        FontProgram fp = FontProgramFactory.createFont(fontBytes);
        return PdfFontFactory.createFont(fp, PdfEncodings.IDENTITY_H); // embed mặc định
    }

    // ================== Helper: Milestones Layout ==================
    private void drawMilestones(PdfDocument pdf,
                                PdfAcroForm acro,
                                PdfFont font,
                                List<MilestoneRequest> milestones) {

        if (milestones == null || milestones.isEmpty()) {
            return;
        }

        Rectangle rect;
        PdfPage page;

        PdfFormField frame = acro.getField("MilestonesFrame");
        if (frame != null && !frame.getWidgets().isEmpty()) {
            PdfWidgetAnnotation w = frame.getWidgets().get(0);
            rect = w.getRectangle().toRectangle();
            page = w.getPage();
        } else {
            // Fallback: toạ độ bạn đã đo (inches -> points)
            final float INCH = 72f;
            float x = 0.9732f * INCH;
            float y = 0.4268f * INCH;
            float w = 6.4629f * INCH;
            float h = 5.9503f * INCH;
            rect = new Rectangle(x, y, w, h);
            page = pdf.getPage(3); // đúng trang chứa khu vực milestones
        }

        PdfCanvas pdfCanvas = new PdfCanvas(page);
        Canvas canvas = new Canvas(pdfCanvas, rect);
        java.time.format.DateTimeFormatter df = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");

        com.itextpdf.layout.element.List outer =
                new com.itextpdf.layout.element.List()
                        .setSymbolIndent(12)
                        .setFont(font)
                        .setFontSize(10.5f)
                        .setListSymbol("\u25A0"); // ■

        for (int i = 0; i < milestones.size(); i++) {
            var m = milestones.get(i);

            String title = nvl(m.getTitle(), "Cột mốc " + (i + 1));
            String due   = (m.getDueDate() != null) ? m.getDueDate().format(df) : "......";

            ListItem li = new ListItem();
            li.add(new Paragraph(title + "  (Đến hạn: " + due + ")")
                    .setBold()
                    .setFont(font)
                    .setFontSize(11));

//        for (int i = 0; i < milestones.size(); i++) {
//            var m = milestones.get(i);
//
//            ListItem li = new ListItem();
//            li.add(new Paragraph("Cột mốc " + (i + 1) + ":")
//                    .setBold()
//                    .setFont(font)
//                    .setFontSize(11));

            com.itextpdf.layout.element.List inner =
                    new com.itextpdf.layout.element.List()
                            .setListSymbol("\u2022") // •
                            .setSymbolIndent(10)
                            .setMarginLeft(8)
                            .setFont(font)
                            .setFontSize(10.5f);

            String moneyLine = "Số tiền: " + nvl(m.getAmount(), ".....................") + " VND";
            ListItem moneyItem = new ListItem();
            moneyItem.add(new Paragraph(moneyLine).setFont(font));
            inner.add(moneyItem);

            if (!isBlank(m.getDescription())) {
                ListItem noteItem = new ListItem();
                noteItem.add(new Paragraph("Mô tả: " + m.getDescription()).setFont(font));
                inner.add(noteItem);
            }

            li.add(inner);
            outer.add(li);
        }

        canvas.add(outer);
        canvas.close();
    }

    // ================== Helper: Checkbox ==================
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

    // ================== Helper: Tổng hợp & Field map ==================
    private Map<String, String> mapDtoToPdfFields(ContractPdfFillRequest req) {
        DateTimeFormatter d = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        Map<String, String> m = new LinkedHashMap<>();

        // Header
        m.put("Text Box 1",  ns(req.getContractNo()));
        m.put("Text Box 2",  req.getSignDate() != null ? req.getSignDate().format(d) : "");
        m.put("Text Box 3",  ns(req.getSignPlace()));

        // Bên A
        m.put("Text Box 4",  ns(req.getAName()));
        m.put("Text Box 5",  ns(req.getACccd()));
        m.put("Text Box 6",  req.getACccdIssueDate() != null ? req.getACccdIssueDate().format(d) : "");
        m.put("Text Box 7",  ns(req.getACccdIssuePlace()));
        m.put("Text Box 8",  ns(req.getAAddress()));
        m.put("Text Box 9",  ns(req.getAPhone()));
        m.put("Text Box 10", ns(req.getARepresentative()));
        m.put("Text Box 11", ns(req.getATitle()));
        m.put("Text Box 12", ns(req.getAPoANo()));

        // Bên B
        m.put("Text Box 13", ns(req.getBName()));
        m.put("Text Box 14", ns(req.getBCccd()));
        m.put("Text Box 15", req.getBCccdIssueDate() != null ? req.getBCccdIssueDate().format(d) : "");
        m.put("Text Box 16", ns(req.getBCccdIssuePlace()));
        m.put("Text Box 17", ns(req.getBAddress()));
        m.put("Text Box 18", ns(req.getBPhone()));
        m.put("Text Box 19", ns(req.getBRepresentative()));
        m.put("Text Box 20", ns(req.getBTitle()));
        m.put("Text Box 21", ns(req.getBPoANo()));

        // Hạng mục
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

        // 37/38/39/42 sẽ bị ghi đè bằng kết quả tính toán ở fillTemplate()
        return m;
    }

    // ================== Helper: Validate Milestones ==================
    private void validateMilestonesAmount(List<MilestoneRequest> milestones, BigDecimal grandTotal) {
        if (milestones == null || milestones.isEmpty()) {
            throw new AppException(ErrorCode.BAD_REQUEST); // yêu cầu mốc nhưng không có mốc nào
        }

        BigDecimal remaining = grandTotal; // số tiền còn lại của dự án (sum + VAT)
        for (int i = 0; i < milestones.size(); i++) {
            MilestoneRequest m = milestones.get(i);

            // Validate bắt buộc
            if (m.getTitle() == null || m.getTitle().isBlank()
                    || m.getAmount() == null || m.getAmount().isBlank()
                    || m.getDueDate() == null) {
                throw new AppException(ErrorCode.BAD_REQUEST);
            }

            BigDecimal amt = parseMoney(m.getAmount());
            if (amt.signum() <= 0) {
                // số tiền mỗi mốc phải > 0
                throw new AppException(ErrorCode.BAD_REQUEST);
            }

            // Quy tắc: mốc hiện tại không vượt số tiền còn lại
            if (amt.compareTo(remaining) > 0) {
                log.warn("Milestone #{} amount {} exceeds remaining {}", (i + 1), amt, remaining);
                throw new AppException(ErrorCode.BAD_REQUEST);
            }

            remaining = remaining.subtract(amt);
        }

        // KHÔNG bắt buộc tổng == grandTotal (nếu cần, mở comment bên dưới)
        // if (remaining.compareTo(BigDecimal.ZERO) != 0) {
        //     throw new AppException(ErrorCode.BAD_REQUEST);
        // }
    }

    // ================== Helper: Tính tiền ==================
    private record MoneyTotals(BigDecimal sum, BigDecimal vat, BigDecimal grandTotal) {}

    private MoneyTotals computeTotals(ContractPdfFillRequest req) {
        BigDecimal sum = BigDecimal.ZERO;
        sum = sum.add(parseMoney(req.getLine1Amount()));
        sum = sum.add(parseMoney(req.getLine2Amount()));
        sum = sum.add(parseMoney(req.getLine3Amount()));

        // VAT tiền = sum * 8%
        BigDecimal vat = sum.multiply(DEFAULT_VAT_RATE).setScale(0, RoundingMode.HALF_UP);
        // Grand total = sum + vat   (chuẩn hoá nghiệp vụ)
        BigDecimal total = sum.add(vat);
        return new MoneyTotals(sum, vat, total);
    }

    private BigDecimal parseMoney(String s) {
        if (s == null || s.isBlank()) return BigDecimal.ZERO;
        // nhận "10,000,000" hoặc "10.000.000" hoặc "10800000"
        String digits = s.replaceAll("[^0-9-]", "");
        if (digits.isBlank()) return BigDecimal.ZERO;
        return new BigDecimal(digits);
    }

    private String formatMoney(BigDecimal v) {
        NumberFormat nf = NumberFormat.getInstance(Locale.US); // "10,000,000"
        nf.setMaximumFractionDigits(0);
        nf.setMinimumFractionDigits(0);
        return nf.format(v);
    }

    private String ns(Object o) { return o == null ? "" : String.valueOf(o); }
    private boolean isBlank(String s) { return s == null || s.isBlank(); }
    private String nvl(String s, String def) { return isBlank(s) ? def : s; }
}