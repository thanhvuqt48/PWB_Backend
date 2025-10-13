//
//
//package com.fpt.producerworkbench.service;
//
//import com.fpt.producerworkbench.common.ContractDocumentType;
//import com.fpt.producerworkbench.common.ContractStatus;
//import com.fpt.producerworkbench.common.SigningMode;
//import com.fpt.producerworkbench.common.SigningOrderType;
//import com.fpt.producerworkbench.configuration.SignNowClient;
//import com.fpt.producerworkbench.dto.request.ContractInviteRequest;
//import com.fpt.producerworkbench.dto.response.StartSigningResponse;
//import com.fpt.producerworkbench.entity.Contract;
//import com.fpt.producerworkbench.entity.ContractDocument;
//import com.fpt.producerworkbench.exception.AppException;
//import com.fpt.producerworkbench.exception.ErrorCode;
//import com.fpt.producerworkbench.repository.ContractDocumentRepository;
//import com.fpt.producerworkbench.repository.ContractRepository;
//import com.fpt.producerworkbench.storage.StorageService;
//import jakarta.transaction.Transactional;
//import lombok.RequiredArgsConstructor;
//import org.springframework.security.core.Authentication;
//import org.springframework.stereotype.Service;
//
//import java.util.*;
//
//@Service
//@RequiredArgsConstructor
//public class ContractInviteService {
//    private final ContractRepository contractRepository;
//    private final ContractDocumentRepository contractDocumentRepository;
//    private final StorageService storageService;
//    private final SignNowClient signNowClient;
//
//    @Transactional
//    public StartSigningResponse invite(Authentication auth, Long contractId, ContractInviteRequest req) {
//        Contract c = contractRepository.findById(contractId)
//                .orElseThrow(() -> new AppException(ErrorCode.BAD_REQUEST));
//
//        boolean sequential = req.getSigningOrder() == SigningOrderType.SEQUENTIAL;
//
//        // 1) upload doc nếu chưa có
////        if (c.getSignnowDocumentId() == null) {
////            byte[] pdf;
////            if (req.getPdfBase64() != null && !req.getPdfBase64().isBlank()) {
////                // Fallback: vẫn cho phép client gửi base64 nếu muốn
////                pdf = Base64.getDecoder().decode(req.getPdfBase64());
////            } else {
////                // Lấy bản FILLED mới nhất từ storage
////                ContractDocument filled = contractDocumentRepository
////                        .findTopByContract_IdAndTypeOrderByVersionDesc(contractId, ContractDocumentType.FILLED)
////                        .orElseThrow(() -> new AppException(ErrorCode.BAD_REQUEST)); // chưa soạn PDF
////
////                pdf = storageService.load(filled.getStorageUrl());
////            }
////            String docId = signNowClient.uploadDocument(pdf, "contract-" + contractId + ".pdf");
////            c.setSignnowDocumentId(docId);
////        }
//        if (c.getSignnowDocumentId() == null) {
//            byte[] pdf;
//            if (req.getPdfBase64() != null && !req.getPdfBase64().isBlank()) {
//                pdf = Base64.getDecoder().decode(req.getPdfBase64());
//            } else {
//                var filled = contractDocumentRepository
//                        .findTopByContract_IdAndTypeOrderByVersionDesc(contractId, ContractDocumentType.FILLED)
//                        .orElseThrow(() -> new AppException(ErrorCode.BAD_REQUEST));
//                pdf = storageService.load(filled.getStorageUrl());
//            }
//            String docId = signNowClient.uploadDocument(pdf, "contract-"+contractId+".pdf");
//            c.setSignnowDocumentId(docId);
//        }
//
//
//        // 2) build recipients
//        List<Map<String,Object>> to = new ArrayList<>();
//        for (var s : req.getSigners()) {
//            Map<String,Object> m = new HashMap<>();
//            m.put("email", s.getEmail());
//            if (s.getOrder()!=null) m.put("order", s.getOrder());
//            if (Boolean.TRUE.equals(req.getUseFieldInvite()) && s.getRoleId()!=null) {
//                m.put("role_id", s.getRoleId());
//            }
//            to.add(m);
//        }
//
//        // 3) send invite
//        StartSigningResponse resp = new StartSigningResponse();
//        if (req.getSigningMode() == SigningMode.EMBEDDED) {
//            String url = signNowClient.createEmbeddedInviteAndLink(c.getSignnowDocumentId(), to, sequential);
//            resp.setInviteId("embedded");
//            resp.setEmbeddedLink(url);
//        } else {
//            Map<String,Object> inv = Boolean.TRUE.equals(req.getUseFieldInvite())
//                    ? signNowClient.createFieldInvite(c.getSignnowDocumentId(), to, sequential, /*from*/ null)
//                    : signNowClient.createFreeFormInvite(c.getSignnowDocumentId(), to, /*from*/ null);
//            resp.setInviteId((String) inv.getOrDefault("id", "invite"));
//        }
//
//        // 4) update status nội bộ
//        c.setStatus(ContractStatus.DRAFT);
//        contractRepository.save(c);
//
//        return resp;
//    }
//}

package com.fpt.producerworkbench.service;

import com.fpt.producerworkbench.common.ContractDocumentType;
import com.fpt.producerworkbench.common.ContractStatus;
import com.fpt.producerworkbench.common.SigningMode;
import com.fpt.producerworkbench.common.SigningOrderType;
import com.fpt.producerworkbench.configuration.SignNowClient;
import com.fpt.producerworkbench.dto.request.ContractInviteRequest;
import com.fpt.producerworkbench.dto.response.StartSigningResponse;
import com.fpt.producerworkbench.entity.Contract;
import com.fpt.producerworkbench.entity.ContractDocument;
import com.fpt.producerworkbench.exception.AppException;
import com.fpt.producerworkbench.exception.ErrorCode;
import com.fpt.producerworkbench.repository.ContractDocumentRepository;
import com.fpt.producerworkbench.repository.ContractRepository;
import com.fpt.producerworkbench.storage.StorageService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class ContractInviteService {

    private final ContractRepository contractRepository;
    private final ContractDocumentRepository contractDocumentRepository;
    private final StorageService storageService;
    private final SignNowClient signNowClient;

    @Transactional
    public StartSigningResponse invite(Authentication auth, Long contractId, ContractInviteRequest req) {
        // ====== Logging đầu vào (Bước 4) ======
        log.info("[Invite] contractId={}, signingMode={}, signingOrder={}, useFieldInvite={}, signers={}",
                contractId, req.getSigningMode(), req.getSigningOrder(), req.getUseFieldInvite(), req.getSigners());

        // ====== Validate cơ bản (Bước 3) ======
        Contract c = contractRepository.findById(contractId)
                .orElseThrow(() -> new AppException(ErrorCode.CONTRACT_NOT_FOUND));

        if (req.getSigners() == null || req.getSigners().isEmpty()) {
            throw new AppException(ErrorCode.SIGNERS_REQUIRED);
        }
        boolean sequential = req.getSigningOrder() == SigningOrderType.SEQUENTIAL;

        // ====== 1) Upload document lên SignNow nếu chưa có docId (Bước 2) ======
        if (c.getSignnowDocumentId() == null) {
            byte[] pdfBytes;

            // a) Nếu client vẫn gửi pdfBase64 thì ưu tiên dùng
            if (req.getPdfBase64() != null && !req.getPdfBase64().isBlank()) {
                try {
                    pdfBytes = Base64.getDecoder().decode(req.getPdfBase64());
                    log.info("[Invite] Using pdfBase64 from request, size={} bytes", pdfBytes.length);
                } catch (IllegalArgumentException e) {
                    log.warn("[Invite] pdfBase64 decode failed", e);
                    throw new AppException(ErrorCode.PDF_BASE64_INVALID);
                }
            } else {
                // b) Đọc bản FILLED mới nhất từ storage
                ContractDocument filled = contractDocumentRepository
                        .findTopByContract_IdAndTypeOrderByVersionDesc(contractId, ContractDocumentType.FILLED)
                        .orElseThrow(() -> new AppException(ErrorCode.CONTRACT_FILLED_PDF_NOT_FOUND));

                try {
                    pdfBytes = storageService.load(filled.getStorageUrl());
                    log.info("[Invite] Loaded FILLED from storage: url={}, size={} bytes",
                            filled.getStorageUrl(), pdfBytes == null ? 0 : pdfBytes.length);
                } catch (RuntimeException ex) {
                    log.error("[Invite] Cannot read FILLED from storage", ex);
                    throw new AppException(ErrorCode.STORAGE_READ_FAILED);
                }
            }

            // c) Upload lên SignNow
            try {
                String docId = signNowClient.uploadDocument(pdfBytes, "contract-" + contractId + ".pdf");
                c.setSignnowDocumentId(docId);
                log.info("[Invite] Uploaded to SignNow, documentId={}", docId);
            } catch (WebClientResponseException wex) {
                // Tránh getRawStatusCode() (đã deprecated)
                int status = wex.getStatusCode().value();
                String body  = wex.getResponseBodyAsString();
                log.error("[Invite] SignNow upload failed: status={} {}, body={}", status, wex.getStatusCode(), body, wex);
                throw new AppException(ErrorCode.SIGNNOW_UPLOAD_FAILED);
            } catch (Exception ex) {
                log.error("[Invite] SignNow upload failed (unexpected)", ex);
                throw new AppException(ErrorCode.SIGNNOW_UPLOAD_FAILED);
            }
        }

        // ====== 2) Build recipients ======
        List<Map<String, Object>> to = new ArrayList<>();
        for (var s : req.getSigners()) {
            if (s.getEmail() == null || s.getEmail().isBlank()) {
                throw new AppException(ErrorCode.SIGNER_EMAIL_REQUIRED);
            }
            Map<String, Object> m = new HashMap<>();
            m.put("email", s.getEmail());
            if (s.getOrder() != null) m.put("order", s.getOrder());

            if (Boolean.TRUE.equals(req.getUseFieldInvite())) {
                if (s.getRoleId() == null || s.getRoleId().isBlank()) {
                    // Nếu dùng Field Invite (có text tags), roleId là bắt buộc
                    throw new AppException(ErrorCode.ROLE_ID_REQUIRED);
                }
                m.put("role_id", s.getRoleId());
            }
            to.add(m);
        }

        // ====== 3) Gửi invite ======
        StartSigningResponse resp = new StartSigningResponse();
        try {
            if (req.getSigningMode() == SigningMode.EMBEDDED) {
                // signers: email (+ role_id nếu đã add field)
                String url = signNowClient.createEmbeddedInviteAndLink(c.getSignnowDocumentId(), to, sequential);
                resp.setInviteId("embedded");
                resp.setEmbeddedLink(url);
            } else {
                Map<String, Object> inv = Boolean.TRUE.equals(req.getUseFieldInvite())
                        ? signNowClient.createFieldInvite(c.getSignnowDocumentId(), to, sequential, null)
                        : signNowClient.createFreeFormInvite(c.getSignnowDocumentId(), to, null);
                resp.setInviteId((String) inv.getOrDefault("id", "invite"));
            }
        } catch (WebClientResponseException wex) {
            int status = wex.getStatusCode().value();
            String body  = wex.getResponseBodyAsString();
            log.error("[Invite] SignNow invite failed: status={} {}, body={}", status, wex.getStatusCode(), body, wex);
            throw new AppException(ErrorCode.SIGNNOW_INVITE_FAILED);
        }
         catch (Exception ex) {
            log.error("[Invite] SignNow invite failed (unexpected)", ex);
            throw new AppException(ErrorCode.SIGNNOW_INVITE_FAILED);
        }

        // ====== 4) Cập nhật trạng thái nội bộ ======
        c.setStatus(ContractStatus.DRAFT);
        // nếu có cột signnow_status: c.setSignnowStatus(ContractStatus.OUT_FOR_SIGNATURE);
        contractRepository.save(c);

        return resp;
    }
}

