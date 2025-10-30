package com.fpt.producerworkbench.service.impl;

// Các import sau đã bị comment vì service bị vô hiệu hóa
// import com.fpt.producerworkbench.common.ContractDocumentType;
// import com.fpt.producerworkbench.common.ContractStatus;
// import com.fpt.producerworkbench.configuration.SignNowClient;
// import com.fpt.producerworkbench.entity.Contract;
// import com.fpt.producerworkbench.entity.ContractDocument;
// import com.fpt.producerworkbench.repository.ContractDocumentRepository;
// import com.fpt.producerworkbench.repository.ContractRepository;
// import com.fpt.producerworkbench.service.FileKeyGenerator;
// import com.fpt.producerworkbench.service.FileStorageService;
// import lombok.RequiredArgsConstructor; // Không cần thiết
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@Slf4j
// CHÚ Ý: Service này đã bị vô hiệu hóa - webhook tự động lưu bản ký cuối đã bị tắt
// Chỉ sử dụng API thủ công: POST /api/v1/contracts/{id}/signed
public class SignNowWebhookService {

    // Các field sau đã bị comment vì service bị vô hiệu hóa
    // private final ContractRepository contractRepository;
    // private final ContractDocumentRepository contractDocumentRepository;
    // private final FileStorageService fileStorageService;
    // private final FileKeyGenerator fileKeyGenerator;
    // private final SignNowClient signNowClient;

    // METHOD NÀY ĐÃ BỊ VÔ HIỆU HÓA - KHÔNG TỰ ĐỘNG LƯU BẢN KÝ CUỐI QUA WEBHOOK
    public void handle(Map<String, Object> payload) {
        log.warn("SignNowWebhookService.handle() called but webhook auto-save is DISABLED. Use manual API instead.");
        return; // Vô hiệu hóa hoàn toàn
        
        /* ORIGINAL CODE - ĐÃ BỊ VÔ HIỆU HÓA
        try {
            String docId = String.valueOf(payload.getOrDefault("document_id", ""));
            String event = String.valueOf(payload.getOrDefault("event", ""));

            if (docId.isBlank()) return;

            Contract c = contractRepository.findBySignnowDocumentId(docId)
                    .orElse(null);
            if (c == null) return; // không xác định được hợp đồng

            if ("document.complete".equalsIgnoreCase(event) || "document.completed".equalsIgnoreCase(event)) {
                // nếu đã có bản SIGNED thì bỏ qua (idempotent)
                ContractDocument latest = contractDocumentRepository
                        .findTopByContract_IdAndTypeOrderByVersionDesc(c.getId(), ContractDocumentType.SIGNED)
                        .orElse(null);
                if (latest == null) {
                    try {
                        byte[] pdf = signNowClient.downloadFinalPdf(docId, false);
                        int nextVer = 1;
                        String objectKey = fileKeyGenerator.generateContractDocumentKey(c.getId(), "signed_v" + nextVer + ".pdf");
                        fileStorageService.uploadBytes(pdf, objectKey, "application/pdf");

                        ContractDocument doc = new ContractDocument();
                        doc.setContract(c);
                        doc.setType(ContractDocumentType.SIGNED);
                        doc.setVersion(nextVer);
                        doc.setStorageUrl(objectKey);
                        contractDocumentRepository.save(doc);
                    } catch (Exception ex) {
                        log.error("Webhook download/save signed failed: {}", ex.getMessage());
                    }
                }

                c.setSignnowStatus(ContractStatus.COMPLETED);
                c.setStatus(ContractStatus.COMPLETED);
                contractRepository.save(c);
            } else if ("document.declined".equalsIgnoreCase(event)) {
                c.setSignnowStatus(ContractStatus.DECLINED);
                c.setStatus(ContractStatus.DECLINED);
                contractRepository.save(c);
            }
        } catch (Exception e) {
            log.error("Error handling SignNow webhook: {}", e.getMessage());
        }
        */
    }
}


