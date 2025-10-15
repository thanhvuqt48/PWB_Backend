package com.fpt.producerworkbench.service.impl;

import com.fpt.producerworkbench.common.ContractDocumentType;
import com.fpt.producerworkbench.common.ContractStatus;
import com.fpt.producerworkbench.configuration.SignNowClient;
import com.fpt.producerworkbench.dto.response.SignedContractResponse;
import com.fpt.producerworkbench.entity.Contract;
import com.fpt.producerworkbench.entity.ContractDocument;
import com.fpt.producerworkbench.exception.AppException;
import com.fpt.producerworkbench.exception.ErrorCode;
import com.fpt.producerworkbench.repository.ContractDocumentRepository;
import com.fpt.producerworkbench.repository.ContractRepository;
import com.fpt.producerworkbench.service.ContractSigningService;
import com.fpt.producerworkbench.service.StorageService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@Service
@RequiredArgsConstructor
@Slf4j
public class ContractSigningServiceImpl implements ContractSigningService {

    private final ContractRepository contractRepository;
    private final ContractDocumentRepository contractDocumentRepository;
    private final StorageService storageService;
    private final SignNowClient signNowClient;


    @Override
    @Transactional
    public SignedContractResponse saveSignedAndComplete(Long contractId, boolean withHistory) {
        Contract c = contractRepository.findById(contractId)
                .orElseThrow(() -> new AppException(ErrorCode.CONTRACT_NOT_FOUND));
        if (c.getSignnowDocumentId() == null || c.getSignnowDocumentId().isBlank()) {
            throw new AppException(ErrorCode.SIGNNOW_DOC_ID_NOT_FOUND);
        }

        byte[] pdf;
        try {
            pdf = signNowClient.downloadFinalPdf(c.getSignnowDocumentId(), withHistory);
            log.info("[Signed] Downloaded bytes={}", (pdf == null ? 0 : pdf.length));
        } catch (WebClientResponseException ex) {
            int sc = ex.getStatusCode().value();
            if (sc == 400 || sc == 409 || sc == 422) {
                log.warn("[Signed] Collapsed not ready: status={} body={}", sc, ex.getResponseBodyAsString());
                throw new AppException(ErrorCode.SIGNNOW_DOC_NOT_COMPLETED);
            }
            log.error("[Signed] Download failed: status={} body={}", sc, ex.getResponseBodyAsString());
            throw new AppException(ErrorCode.SIGNNOW_DOWNLOAD_FAILED);
        } catch (IllegalStateException ex) {
            // 200 OK nhưng body rỗng
            log.error("[Signed] Download failed: empty body (200 OK)", ex);
            throw new AppException(ErrorCode.SIGNNOW_DOWNLOAD_FAILED);
        } catch (Exception ex) {
            log.error("[Signed] Download failed (unexpected)", ex);
            throw new AppException(ErrorCode.SIGNNOW_DOWNLOAD_FAILED);
        }


        ContractDocument latest = contractDocumentRepository
                .findTopByContract_IdAndTypeOrderByVersionDesc(contractId, ContractDocumentType.SIGNED)
                .orElse(null);
        int nextVer = latest == null ? 1 : latest.getVersion() + 1;

        String storageUrl = "contracts/" + contractId + "/signed_v" + nextVer + ".pdf";
        storageService.save(pdf, storageUrl);

        ContractDocument doc = new ContractDocument();
        doc.setContract(c);
        doc.setType(ContractDocumentType.SIGNED);
        doc.setVersion(nextVer);
        doc.setStorageUrl(storageUrl);
        contractDocumentRepository.save(doc);

        c.setSignnowStatus(ContractStatus.COMPLETED);
        contractRepository.save(c);

        return SignedContractResponse.builder()
                .storageUrl(storageUrl)
                .version(nextVer)
                .size(pdf == null ? 0 : pdf.length)
                .build();
    }
}
