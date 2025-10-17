package com.fpt.producerworkbench.service.impl;

import com.fpt.producerworkbench.common.ContractDocumentType;
import com.fpt.producerworkbench.common.ContractStatus;
import com.fpt.producerworkbench.configuration.SignNowClient;
import com.fpt.producerworkbench.entity.Contract;
import com.fpt.producerworkbench.entity.ContractDocument;
import com.fpt.producerworkbench.repository.ContractDocumentRepository;
import com.fpt.producerworkbench.repository.ContractRepository;
import com.fpt.producerworkbench.service.FileKeyGenerator;
import com.fpt.producerworkbench.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class SignNowWebhookService {

    private final ContractRepository contractRepository;
    private final ContractDocumentRepository contractDocumentRepository;
    private final FileStorageService fileStorageService;
    private final FileKeyGenerator fileKeyGenerator;
    private final SignNowClient signNowClient;

    public void handle(Map<String, Object> payload) {
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
    }
}


