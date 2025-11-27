package com.fpt.producerworkbench.service.impl;

import com.fpt.producerworkbench.common.AddendumDocumentType;
import com.fpt.producerworkbench.common.ContractStatus;
import com.fpt.producerworkbench.common.MilestoneStatus;
import com.fpt.producerworkbench.common.PaymentType;
import com.fpt.producerworkbench.dto.request.ContractAddendumMilestoneItemRequest;
import com.fpt.producerworkbench.dto.request.ContractAddendumPdfFillRequest;
import com.fpt.producerworkbench.entity.AddendumDocument;
import com.fpt.producerworkbench.entity.Contract;
import com.fpt.producerworkbench.entity.ContractAddendum;
import com.fpt.producerworkbench.entity.ContractAddendumMilestone;
import com.fpt.producerworkbench.entity.ContractParty;
import com.fpt.producerworkbench.entity.Milestone;
import com.fpt.producerworkbench.exception.AppException;
import com.fpt.producerworkbench.exception.ErrorCode;
import com.fpt.producerworkbench.repository.*;
import com.fpt.producerworkbench.service.ContractAddendumPdfService;
import com.fpt.producerworkbench.service.FileKeyGenerator;
import com.fpt.producerworkbench.service.FileStorageService;
import com.fpt.producerworkbench.service.ProjectPermissionService;
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
import com.itextpdf.layout.element.IBlockElement;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Text;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class ContractAddendumPdfServiceImpl implements ContractAddendumPdfService {

    private static final BigDecimal VAT_RATE = new BigDecimal("0.05");
    private static final BigDecimal PIT_RATE = new BigDecimal("0.02");

    private final ContractRepository contractRepository;
    private final ContractAddendumRepository addendumRepository;
    private final ContractAddendumMilestoneRepository addendumMilestoneRepository;
    private final AddendumDocumentRepository addendumDocumentRepository;
    private final FileStorageService fileStorageService;
    private final FileKeyGenerator fileKeyGenerator;
    private final ProjectPermissionService projectPermissionService;
    private final MilestoneRepository milestoneRepository;
    private final com.fpt.producerworkbench.repository.ContractPartyRepository contractPartyRepository;

    @Value("${pwb.addendum.template}")
    private Resource addendumTemplate;

    @Value("${pwb.addendum.milestone-template}")
    private Resource addendumMilestoneTemplate;

    @Value("${pwb.contract.font}")
    private Resource fontResource;

    static final float INCH = 72f;

    static Rectangle inchRect(float l, float b, float w, float h) {
        return new Rectangle(l * INCH, b * INCH, w * INCH, h * INCH);
    }

    static final Rectangle ADD_FIRST_FALLBACK_RECT = inchRect(1.0f, 2.6f, 6.5f, 5.8f);
    static final Rectangle ADD_CONT_RECT          = inchRect(1.0f, 2.4f, 6.5f, 7.8f);

    private record PageAndRect(PdfPage page, Rectangle rect) {
    }

    private PageAndRect resolveAdditionalFirstArea(PdfDocument pdf, PdfAcroForm acro) {
        PdfFormField f = (acro != null) ? acro.getField("additional") : null;
        if (f != null && !f.getWidgets().isEmpty() && f.getWidgets().get(0).getPage() != null) {
            PdfWidgetAnnotation w = f.getWidgets().get(0);
            return new PageAndRect(w.getPage(), w.getRectangle().toRectangle());
        }
        int desired = Math.min(2, Math.max(1, pdf.getNumberOfPages()));
        return new PageAndRect(pdf.getPage(desired), ADD_FIRST_FALLBACK_RECT);
    }

    private void ensureHasAtLeastPages(PdfDocument pdf, int desiredPage) {
        int numPages = pdf.getNumberOfPages();
        if (numPages < desiredPage) {
            PageSize base = (numPages >= 1) ? new PageSize(pdf.getPage(1).getPageSize()) : PageSize.A4;
            for (int i = numPages; i < desiredPage; i++) pdf.addNewPage(base);
        }
    }

    private PdfFont loadUnicodeFont() throws Exception {
        byte[] fontBytes;
        try (InputStream fin = fontResource.getInputStream()) {
            fontBytes = StreamUtils.copyToByteArray(fin);
        }
        FontProgram fp = FontProgramFactory.createFont(fontBytes);
        return PdfFontFactory.createFont(fp, PdfEncodings.IDENTITY_H);
    }

    @Override
    public byte[] fillAddendum(Authentication auth, Long contractId, ContractAddendumPdfFillRequest req) {
        if (contractId == null) throw new AppException(ErrorCode.BAD_REQUEST);

        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new AppException(ErrorCode.BAD_REQUEST));

        var perms = projectPermissionService.checkContractPermissions(auth, contract.getProject().getId());
        if (!perms.isCanCreateContract()) throw new AppException(ErrorCode.ACCESS_DENIED);

        // Tự động load snapshot thông tin Bên A/B từ ContractParty để không phải nhập lại khi soạn phụ lục
        ContractParty party = contractPartyRepository.findByContractId(contractId)
                .orElseThrow(() -> new AppException(ErrorCode.BAD_REQUEST));
        applyPartyToAddendumRequest(party, req);

        boolean milestoneMode = PaymentType.MILESTONE.equals(contract.getPaymentType());

        if (milestoneMode) {
            if (req.getMilestones() == null || req.getMilestones().isEmpty()) {
                throw new AppException(ErrorCode.BAD_REQUEST);
            }
        } else {
            if (req.getMilestones() != null && !req.getMilestones().isEmpty()) {
                throw new AppException(ErrorCode.BAD_REQUEST);
            }
            if (req.getNumOfMoney() == null || req.getNumOfMoney().signum() <= 0) {
                throw new AppException(ErrorCode.BAD_REQUEST);
            }
        }

        // Tìm phụ lục mới nhất (theo addendumNumber và version)
        ContractAddendum latestAddendum = addendumRepository
                .findFirstByContractIdOrderByAddendumNumberDescVersionDesc(contractId)
                .orElse(null);

        ContractAddendum add;

        if (latestAddendum == null) {
            // Chưa có phụ lục nào → tạo phụ lục 1, version 1
            add = ContractAddendum.builder()
                    .contract(contract)
                    .title(req.getTitle() != null ? req.getTitle() : "Phụ lục hợp đồng")
                    .addendumNumber(1)
                    .version(1)
                    .effectiveDate(req.getEffectiveDate())
                    .signnowStatus(ContractStatus.DRAFT)
                    .build();
        } else if (ContractStatus.COMPLETED.equals(latestAddendum.getSignnowStatus())) {
            // Phụ lục hiện tại đã COMPLETED → tạo phụ lục mới (addendumNumber + 1), version 1
            int newAddendumNumber = latestAddendum.getAddendumNumber() + 1;
            add = ContractAddendum.builder()
                    .contract(contract)
                    .title(req.getTitle() != null ? req.getTitle() : "Phụ lục hợp đồng")
                    .addendumNumber(newAddendumNumber)
                    .version(1)
                    .effectiveDate(req.getEffectiveDate())
                    .signnowStatus(ContractStatus.DRAFT)
                    .build();
        } else {
            // Phụ lục hiện tại chưa COMPLETED (DRAFT) → tạo version mới của cùng addendumNumber
            int currentAddendumNumber = latestAddendum.getAddendumNumber();
            // Tìm version mới nhất của addendumNumber này
            ContractAddendum latestVersionOfAddendum = addendumRepository
                    .findFirstByContractIdAndAddendumNumberOrderByVersionDesc(contractId, currentAddendumNumber)
                    .orElse(latestAddendum);
            int newVersion = latestVersionOfAddendum.getVersion() + 1;
            
            add = ContractAddendum.builder()
                    .contract(contract)
                    .title(req.getTitle() != null ? req.getTitle() : latestAddendum.getTitle())
                    .addendumNumber(currentAddendumNumber)
                    .version(newVersion)
                    .effectiveDate(req.getEffectiveDate() != null ? req.getEffectiveDate() : latestAddendum.getEffectiveDate())
                    .signnowStatus(ContractStatus.DRAFT)
                    .build();
        }

        add.setPitTax(BigDecimal.ZERO);
        add.setVatTax(BigDecimal.ZERO);

        if (!milestoneMode) {
            BigDecimal base = req.getNumOfMoney() != null ? req.getNumOfMoney() : BigDecimal.ZERO;
            add.setNumOfMoney(base);
            add.setNumOfEdit(req.getNumOfEdit());
            add.setNumOfRefresh(req.getNumOfRefresh());

            BigDecimal pit = base.multiply(PIT_RATE).setScale(2, RoundingMode.HALF_UP);
            BigDecimal vat = base.multiply(VAT_RATE).setScale(2, RoundingMode.HALF_UP);
            add.setPitTax(pit);
            add.setVatTax(vat);
        }

        add = addendumRepository.save(add);

        int pdfStartIndex = 1;

        if (milestoneMode) {
            addendumMilestoneRepository.deleteByAddendumId(add.getId());

            BigDecimal totalPit     = BigDecimal.ZERO;
            BigDecimal totalVat     = BigDecimal.ZERO;
            BigDecimal totalMoney   = BigDecimal.ZERO;
            int        totalEdit    = 0;
            int        totalRefresh = 0;

            List<ContractAddendumMilestoneItemRequest> items =
                    Optional.ofNullable(req.getMilestones()).orElse(Collections.emptyList());

            long existingCount = milestoneRepository.countByContract(contract);
            int newIndex = (int) existingCount + 1;
            pdfStartIndex = newIndex;

            for (ContractAddendumMilestoneItemRequest item : items) {
                if (item == null) continue;

                boolean existing = item.getMilestoneId() != null;

                Integer editDelta    = item.getNumOfEdit()    != null ? item.getNumOfEdit()    : 0;
                Integer refreshDelta = item.getNumOfRefresh() != null ? item.getNumOfRefresh() : 0;

                if (editDelta < 0 || refreshDelta < 0) {
                    throw new AppException(ErrorCode.BAD_REQUEST);
                }

                BigDecimal baseMoney = item.getNumOfMoney() != null ? item.getNumOfMoney() : BigDecimal.ZERO;

                if (baseMoney.signum() < 0) {
                    throw new AppException(ErrorCode.BAD_REQUEST);
                }

                BigDecimal pit = baseMoney.multiply(PIT_RATE).setScale(2, RoundingMode.HALF_UP);
                BigDecimal vat = baseMoney.multiply(VAT_RATE).setScale(2, RoundingMode.HALF_UP);

                ContractAddendumMilestone.ContractAddendumMilestoneBuilder msBuilder = ContractAddendumMilestone.builder()
                        .addendum(add)
                        .numOfMoney(baseMoney)
                        .numOfEdit(editDelta)
                        .numOfRefresh(refreshDelta)
                        .pitTax(pit)
                        .vatTax(vat)
                        .description(item.getDescription());

                if (existing) {
                    Milestone baseMs = milestoneRepository.findById(item.getMilestoneId())
                            .orElseThrow(() -> new AppException(ErrorCode.BAD_REQUEST));

                    if (!baseMs.getContract().getId().equals(contract.getId())) {
                        throw new AppException(ErrorCode.ACCESS_DENIED);
                    }
                    if (MilestoneStatus.COMPLETED.equals(baseMs.getStatus())) {
                        throw new AppException(ErrorCode.BAD_REQUEST);
                    }

                    msBuilder.milestone(baseMs);

                    String finalTitle = (item.getTitle() != null && !item.getTitle().isBlank())
                            ? item.getTitle()
                            : baseMs.getTitle();
                    msBuilder.title(finalTitle);

                } else {
                    if (item.getTitle() == null || item.getTitle().isBlank()) {
                        throw new AppException(ErrorCode.BAD_REQUEST);
                    }
                    if (baseMoney.signum() <= 0) {
                        throw new AppException(ErrorCode.BAD_REQUEST);
                    }

                    msBuilder.milestone(null);
                    msBuilder.title(item.getTitle());
                    msBuilder.itemIndex(newIndex++);
                }

                ContractAddendumMilestone ms = msBuilder.build();
                addendumMilestoneRepository.save(ms);

                totalPit     = totalPit.add(pit);
                totalVat     = totalVat.add(vat);
                totalMoney   = totalMoney.add(baseMoney);

                if (!existing) {
                    totalEdit    += editDelta;
                    totalRefresh += refreshDelta;
                }
            }

            add.setPitTax(totalPit);
            add.setVatTax(totalVat);
            add.setNumOfMoney(totalMoney);
            add.setNumOfEdit(totalEdit);
            add.setNumOfRefresh(totalRefresh);
            addendumRepository.save(add);
        }

        Resource template = milestoneMode ? addendumMilestoneTemplate : addendumTemplate;

        try (InputStream in = template.getInputStream();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            PdfReader reader = new PdfReader(in);
            PdfWriter writer = new PdfWriter(baos);
            PdfDocument pdf = new PdfDocument(reader, writer);
            ensureHasAtLeastPages(pdf, 1);

            PdfFont font = loadUnicodeFont();
            PdfAcroForm acro = PdfAcroForm.getAcroForm(pdf, true);
            acro.setGenerateAppearance(true);

            for (PdfFormField f : acro.getFormFields().values()) {
                f.setFont(font).setFontSize(10f);
            }

            var d = DateTimeFormatter.ofPattern("dd/MM/yyyy");
            Map<String, String> m = new LinkedHashMap<>();
            m.put("Box 1", ns(req.getAddendumNo()));
            m.put("Box 2", req.getSignDate() != null ? req.getSignDate().format(d) : "");
            m.put("Box 3", ns(req.getSignPlace()));

            // Bên A
            m.put("Box 4", ns(req.getAName()));
            m.put("Box 5", ns(req.getAId()));
            m.put("Box 6", req.getAIdIssueDate() != null ? req.getAIdIssueDate().format(d) : "");
            m.put("Box 7", ns(req.getAIdIssuePlace()));
            m.put("Box 8", ns(req.getAAddress()));
            m.put("Box 9", ns(req.getAPhone()));

            // Bên B
            m.put("Box 13", ns(req.getBName()));
            m.put("Box 14", ns(req.getBId()));
            m.put("Box 15", req.getBIdIssueDate() != null ? req.getBIdIssueDate().format(d) : "");
            m.put("Box 16", ns(req.getBIdIssuePlace()));
            m.put("Box 17", ns(req.getBAddress()));
            m.put("Box 18", ns(req.getBPhone()));

            if (!milestoneMode) {
                m.put("numofmoney",  req.getNumOfMoney()  != null ? req.getNumOfMoney().toPlainString() : "");
                m.put("numofedit",   req.getNumOfEdit()   != null ? String.valueOf(req.getNumOfEdit())   : "");
                m.put("numofrefresh",req.getNumOfRefresh()!= null ? String.valueOf(req.getNumOfRefresh()): "");
            }

            m.put("additional", "");

            for (var e : m.entrySet()) {
                PdfFormField f = acro.getField(e.getKey());
                if (f == null) {
                    log.warn("[Addendum] Field '{}' not found, skip.", e.getKey());
                    continue;
                }
                if (f instanceof PdfTextFormField tf && "additional".equalsIgnoreCase(e.getKey())) {
                    tf.setMultiline(true);
                }
                f.setValue(e.getValue() == null ? "" : e.getValue());
            }

            PageAndRect first = resolveAdditionalFirstArea(pdf, acro);
            acro.flattenFields();

            if (milestoneMode) {
                List<ContractAddendumMilestoneItemRequest> newMilestonesForPdf =
                        Optional.ofNullable(req.getMilestones())
                                .orElse(Collections.emptyList())
                                .stream()
                                .filter(it -> it != null && it.getMilestoneId() == null)
                                .toList();

                drawMilestonesAndAdditional(first.page(), first.rect(), font,
                        newMilestonesForPdf, req.getAdditional(), pdf, pdfStartIndex);
            } else {
                drawAdditionalBlockWithHeading(first.page(), first.rect(), font,
                        req.getAdditional(), pdf);
            }

            pdf.close();
            byte[] out = baos.toByteArray();

            String key = fileKeyGenerator.generateContractDocumentKey(contractId, "addendum.pdf");
            String url = fileStorageService.uploadBytes(out, key, "application/pdf");

            AddendumDocument doc = addendumDocumentRepository
                    .findFirstByAddendumIdAndTypeOrderByVersionDesc(add.getId(), AddendumDocumentType.FILLED)
                    .orElse(null);

            boolean createNewDoc = (doc == null) || (add.getVersion() > (doc.getVersion() != null ? doc.getVersion() : 0));

            if (createNewDoc) {
                doc = AddendumDocument.builder()
                        .addendum(add)
                        .type(AddendumDocumentType.FILLED)
                        .version(add.getVersion()) // Sync version với Addendum
                        .storageUrl(url)
                        .build();
            } else {
                doc.setStorageUrl(url);
            }
            addendumDocumentRepository.save(doc);

            return out;

        } catch (Exception ex) {
            log.error("[Addendum] fill error", ex);
            throw new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION);
        }
    }


    private void drawAdditionalBlockWithHeading(
            PdfPage startPage,
            Rectangle rectFirst,
            PdfFont font,
            String raw,
            PdfDocument pdf
    ) {
        PdfPage page = startPage;
        Rectangle rect = rectFirst;
        int pageNum = pdf.getPageNumber(page);
        Canvas canvas = new Canvas(page, rect);
        float remaining = rect.getHeight();

        List<String> clauses = splitClauses(raw);
        if (clauses.isEmpty()) {
            canvas.close();
            return;
        }

        Paragraph heading = new Paragraph("Điều khoản bổ sung")
                .setFont(font).setFontSize(11f)
                .setBold()
                .setMultipliedLeading(1.2f)
                .setMarginTop(0).setMarginBottom(4)
                .setMarginLeft(0).setMarginRight(0);

        float headNeed = measureHeight(divOf(heading, rect.getWidth()), rect);
        if (headNeed > remaining) {
            canvas.close();
            PdfPage newPage = pdf.addNewPage(pageNum + 1, new PageSize(page.getPageSize()));
            pageNum += 1;
            page = newPage;
            rect = ADD_CONT_RECT;
            canvas = new Canvas(page, rect);
            remaining = rect.getHeight();
        }
        canvas.add(heading);
        remaining -= headNeed;

        for (int i = 0; i < clauses.size(); i++) {
            String body = clauses.get(i);
            Paragraph p = new Paragraph()
                    .setFont(font).setFontSize(11f)
                    .setMultipliedLeading(1.2f)
                    .setMarginTop(0).setMarginBottom(0)
                    .setMarginLeft(0).setMarginRight(0);

            p.add(new Text("Điều " + (i + 1) + ". ").setBold());
            p.add(new Text(body));

            float need = measureHeight(divOf(p, rect.getWidth()), rect);
            if (need > remaining) {
                canvas.close();
                PdfPage newPage = pdf.addNewPage(pageNum + 1, new PageSize(page.getPageSize()));
                pageNum += 1;
                page = newPage;
                rect = ADD_CONT_RECT;
                canvas = new Canvas(page, rect);
                remaining = rect.getHeight();
            }
            canvas.add(p);
            remaining -= need;
        }
        canvas.close();
    }

    private void drawMilestonesAndAdditional(
            PdfPage startPage,
            Rectangle rectFirst,
            PdfFont font,
            List<ContractAddendumMilestoneItemRequest> milestones,
            String additional,
            PdfDocument pdf,
            int startIndex
    ) {
        PdfPage page = startPage;
        Rectangle rect = rectFirst;
        int pageNum = pdf.getPageNumber(page);
        Canvas canvas = new Canvas(page, rect);
        float remaining = rect.getHeight();

        boolean hasMilestones = milestones != null && !milestones.isEmpty();

        if (hasMilestones) {
            Paragraph heading = new Paragraph("Cột mốc bổ sung")
                    .setFont(font).setFontSize(11f)
                    .setBold()
                    .setMultipliedLeading(1.2f)
                    .setMarginTop(0).setMarginBottom(4)
                    .setMarginLeft(0).setMarginRight(0);

            float headNeed = measureHeight(divOf(heading, rect.getWidth()), rect);
            if (headNeed > remaining) {
                canvas.close();
                PdfPage newPage = pdf.addNewPage(pageNum + 1, new PageSize(page.getPageSize()));
                pageNum += 1;
                page = newPage;
                rect = ADD_CONT_RECT;
                canvas = new Canvas(page, rect);
                remaining = rect.getHeight();
            }
            canvas.add(heading);
            remaining -= headNeed;

            int index = startIndex;

            for (ContractAddendumMilestoneItemRequest it : milestones) {
                if (it == null) continue;

                String title = (it.getTitle() != null && !it.getTitle().isBlank())
                        ? it.getTitle().trim()
                        : ("Cột mốc " + index);

                String desc = (it.getDescription() != null && !it.getDescription().isBlank())
                        ? it.getDescription().trim()
                        : null;

                Paragraph p = new Paragraph()
                        .setFont(font).setFontSize(11f)
                        .setMultipliedLeading(1.2f)
                        .setMarginTop(0).setMarginBottom(0)
                        .setMarginLeft(0).setMarginRight(0);

                p.add(new Text("Cột mốc " + index + ". ").setBold());
                p.add(new Text(title));

                if (desc != null) {
                    p.add(new Text("\n- Mô tả: " + desc));
                }
                if (it.getNumOfMoney() != null) {
                    p.add(new Text("\n- Giá trị cột mốc: " + it.getNumOfMoney().toPlainString()));
                }
                if (it.getNumOfEdit() != null) {
                    p.add(new Text("\n- Số lần chỉnh sửa: " + it.getNumOfEdit()));
                }
                if (it.getNumOfRefresh() != null) {
                    p.add(new Text("\n- Số lần refresh: " + it.getNumOfRefresh()));
                }

                float need = measureHeight(divOf(p, rect.getWidth()), rect);
                if (need > remaining) {
                    canvas.close();
                    PdfPage newPage = pdf.addNewPage(pageNum + 1, new PageSize(page.getPageSize()));
                    pageNum += 1;
                    page = newPage;
                    rect = ADD_CONT_RECT;
                    canvas = new Canvas(page, rect);
                    remaining = rect.getHeight();
                }
                canvas.add(p);
                remaining -= need;

                index++;
            }
        }

        List<String> clauses = splitClauses(additional);
        if (clauses.isEmpty()) {
            canvas.close();
            return;
        }

        if (hasMilestones) {
            Paragraph spacer = new Paragraph("\n")
                    .setFont(font).setFontSize(11f)
                    .setMargin(0);
            float spNeed = measureHeight(divOf(spacer, rect.getWidth()), rect);
            if (spNeed > remaining) {
                canvas.close();
                PdfPage newPage = pdf.addNewPage(pageNum + 1, new PageSize(page.getPageSize()));
                pageNum += 1;
                page = newPage;
                rect = ADD_CONT_RECT;
                canvas = new Canvas(page, rect);
                remaining = rect.getHeight();
            }
            canvas.add(spacer);
            remaining -= spNeed;
        }

        Paragraph heading2 = new Paragraph("Điều khoản bổ sung")
                .setFont(font).setFontSize(11f)
                .setBold()
                .setMultipliedLeading(1.2f)
                .setMarginTop(0).setMarginBottom(4)
                .setMarginLeft(0).setMarginRight(0);

        float headNeed2 = measureHeight(divOf(heading2, rect.getWidth()), rect);
        if (headNeed2 > remaining) {
            canvas.close();
            PdfPage newPage = pdf.addNewPage(pageNum + 1, new PageSize(page.getPageSize()));
            pageNum += 1;
            page = newPage;
            rect = ADD_CONT_RECT;
            canvas = new Canvas(page, rect);
            remaining = rect.getHeight();
        }
        canvas.add(heading2);
        remaining -= headNeed2;

        for (int i = 0; i < clauses.size(); i++) {
            String body = clauses.get(i);
            Paragraph p = new Paragraph()
                    .setFont(font).setFontSize(11f)
                    .setMultipliedLeading(1.2f)
                    .setMarginTop(0).setMarginBottom(0)
                    .setMarginLeft(0).setMarginRight(0);

            p.add(new Text("Điều " + (i + 1) + ". ").setBold());
            p.add(new Text(body));

            float need = measureHeight(divOf(p, rect.getWidth()), rect);
            if (need > remaining) {
                canvas.close();
                PdfPage newPage = pdf.addNewPage(pageNum + 1, new PageSize(page.getPageSize()));
                pageNum += 1;
                page = newPage;
                rect = ADD_CONT_RECT;
                canvas = new Canvas(page, rect);
                remaining = rect.getHeight();
            }
            canvas.add(p);
            remaining -= need;
        }

        canvas.close();
    }

    private com.itextpdf.layout.element.Div divOf(IBlockElement el, float width) {
        com.itextpdf.layout.element.Div d = new com.itextpdf.layout.element.Div()
                .setWidth(width)
                .setMargin(0)
                .setPadding(0);
        d.add(el);
        return d;
    }

    private float measureHeight(com.itextpdf.layout.element.Div block, Rectangle rect) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PdfWriter writer = new PdfWriter(baos);
        PdfDocument scratchPdf = new PdfDocument(writer);
        com.itextpdf.layout.Document scratchDoc = new com.itextpdf.layout.Document(scratchPdf);
        try {
            var r = block.createRendererSubTree();
            r.setParent(scratchDoc.getRenderer());
            var area = new com.itextpdf.layout.layout.LayoutArea(1, new Rectangle(rect));
            var ctx = new com.itextpdf.layout.layout.LayoutContext(area);
            var res = r.layout(ctx);
            if (res == null || res.getOccupiedArea() == null || res.getOccupiedArea().getBBox() == null) {
                return 14f;
            }
            return res.getOccupiedArea().getBBox().getHeight();
        } finally {
            scratchDoc.close();
        }
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

    private String ns(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    /**
     * Gán thông tin Bên A/B từ snapshot ContractParty vào request phụ lục.
     * Mục tiêu: khi soạn phụ lục không cần nhập lại thông tin hai bên, luôn dùng dữ liệu đã lưu ở hợp đồng.
     */
    private void applyPartyToAddendumRequest(ContractParty party, ContractAddendumPdfFillRequest req) {
        if (party == null || req == null) return;

        // Bên A
        req.setAName(party.getAName());
        req.setAId(party.getAIdNumber());
        req.setAIdIssueDate(party.getAIdIssueDate());
        req.setAIdIssuePlace(party.getAIdIssuePlace());
        req.setAAddress(party.getAAddress());
        req.setAPhone(party.getAPhone());

        // Bên B
        req.setBName(party.getBName());
        req.setBId(party.getBIdNumber());
        req.setBIdIssueDate(party.getBIdIssueDate());
        req.setBIdIssuePlace(party.getBIdIssuePlace());
        req.setBAddress(party.getBAddress());
        req.setBPhone(party.getBPhone());
    }
}