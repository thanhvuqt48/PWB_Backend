package com.fpt.producerworkbench.service.impl;

import com.fpt.producerworkbench.common.ContractDocumentType;
import com.fpt.producerworkbench.dto.request.ContractAddendumPdfFillRequest;
import com.fpt.producerworkbench.entity.Contract;
import com.fpt.producerworkbench.entity.ContractAddendum;
import com.fpt.producerworkbench.entity.ContractDocument;
import com.fpt.producerworkbench.exception.AppException;
import com.fpt.producerworkbench.exception.ErrorCode;
import com.fpt.producerworkbench.repository.ContractAddendumRepository;
import com.fpt.producerworkbench.repository.ContractDocumentRepository;
import com.fpt.producerworkbench.repository.ContractRepository;
import com.fpt.producerworkbench.service.ContractAddendumPdfService;
import com.fpt.producerworkbench.service.ProjectPermissionService;
import com.fpt.producerworkbench.service.FileKeyGenerator;
import com.fpt.producerworkbench.service.FileStorageService;
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
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class ContractAddendumPdfServiceImpl implements ContractAddendumPdfService {

    private final ContractRepository contractRepository;
    private final ContractAddendumRepository addendumRepository;
    private final ContractDocumentRepository contractDocumentRepository;
    private final FileStorageService fileStorageService;
    private final FileKeyGenerator fileKeyGenerator;
    private final ProjectPermissionService projectPermissionService;

    @Value("${pwb.addendum.template}") private Resource addendumTemplate;
    @Value("${pwb.contract.font}")     private Resource fontResource;

    static final float INCH = 72f;
    static Rectangle inchRect(float l, float b, float w, float h) {
        return new Rectangle(l*INCH, b*INCH, w*INCH, h*INCH);
    }
    static final Rectangle ADD_FIRST_FALLBACK_RECT = inchRect(1.0f, 2.6f, 6.5f, 5.8f);
    static final Rectangle ADD_CONT_RECT          = inchRect(1.0f, 2.4f, 6.5f, 7.8f);

    private record PageAndRect(PdfPage page, Rectangle rect) {}

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

        ContractAddendum add = addendumRepository
                .findFirstByContractIdOrderByVersionDesc(contractId)
                .orElse(null);

        if (add == null) {
            add = ContractAddendum.builder()
                    .contract(contract)
                    .title("Phụ lục hợp đồng")
                    .content(Optional.ofNullable(req.getAdditional()).orElse(""))
                    .version(1)
                    .effectiveDate(null)
                    .build();
        } else {
            add.setContent(Optional.ofNullable(req.getAdditional()).orElse(""));
            if (add.getVersion() == 0) add.setVersion(1);
        }
        add = addendumRepository.save(add);

        try (InputStream in = addendumTemplate.getInputStream();
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
            m.put("Box 1",  ns(req.getAddendumNo()));
            m.put("Box 2",  req.getSignDate() != null ? req.getSignDate().format(d) : "");
            m.put("Box 3",  ns(req.getSignPlace()));

            // Bên A
            m.put("Box 4",  ns(req.getAName()));
            m.put("Box 5",  ns(req.getAId()));
            m.put("Box 6",  req.getAIdIssueDate()!=null?req.getAIdIssueDate().format(d):"");
            m.put("Box 7",  ns(req.getAIdIssuePlace()));
            m.put("Box 8",  ns(req.getAAddress()));
            m.put("Box 9",  ns(req.getAPhone()));
            m.put("Box 10", ns(req.getARepresentative()));
            m.put("Box 11", ns(req.getATitle()));
            m.put("Box 12", ns(req.getAPoANo()));

            // Bên B
            m.put("Box 13", ns(req.getBName()));
            m.put("Box 14", ns(req.getBId()));
            m.put("Box 15", req.getBIdIssueDate()!=null?req.getBIdIssueDate().format(d):"");
            m.put("Box 16", ns(req.getBIdIssuePlace()));
            m.put("Box 17", ns(req.getBAddress()));
            m.put("Box 18", ns(req.getBPhone()));
            m.put("Box 19", ns(req.getBRepresentative()));
            m.put("Box 20", ns(req.getBTitle()));
            m.put("Box 21", ns(req.getBPoANo()));

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

            acro.flattenFields();

            PageAndRect first = resolveAdditionalFirstArea(pdf, acro);
            drawAdditionalBlockNoHeading(first.page(), first.rect(), font, req.getAdditional(), pdf);

            pdf.close();
            byte[] out = baos.toByteArray();


            String key = fileKeyGenerator.generateContractDocumentKey(contractId, "addendum.pdf");
            String url = fileStorageService.uploadBytes(out, key, "application/pdf");


            ContractDocument doc = contractDocumentRepository
                    .findFirstByContractIdAndTypeOrderByVersionDesc(contractId, ContractDocumentType.ADDENDUM)
                    .orElse(null);

            if (doc == null) {
                doc = ContractDocument.builder()
                        .contract(contract)
                        .type(ContractDocumentType.ADDENDUM)
                        .version(1)
                        .storageUrl(url)
                        .build();
            } else {
                doc.setStorageUrl(url);
                if (doc.getVersion() == null || doc.getVersion() <= 0) doc.setVersion(1);
            }
            contractDocumentRepository.save(doc);

            return out;

        } catch (Exception ex) {
            log.error("[Addendum] fill error", ex);
            throw new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION);
        }
    }

    private void drawAdditionalBlockNoHeading(PdfPage startPage, Rectangle rectFirst, PdfFont font, String raw, PdfDocument pdf) {
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

        for (int i = 0; i < clauses.size(); i++) {
            String body = clauses.get(i);
            Paragraph p = new Paragraph()
                    .setFont(font).setFontSize(11f)
                    .setMultipliedLeading(1.2f)
                    .setMarginTop(i == 0 ? 0 : 0).setMarginBottom(0)
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
            var ctx  = new com.itextpdf.layout.layout.LayoutContext(area);
            var res  = r.layout(ctx);
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
        String txt = raw.replace("\r\n","\n").trim();
        if (txt.isBlank()) return out;
        String[] parts = txt.split("\\n\\s*\\n");
        for (String p : parts) {
            String s = p.trim();
            s = s.replaceFirst("(?iu)^điều\\s*\\d+\\s*\\.?\\s*", "");
            if (!s.isBlank()) out.add(s);
        }
        return out;
    }

    private String ns(Object o) { return o == null ? "" : String.valueOf(o); }
}
