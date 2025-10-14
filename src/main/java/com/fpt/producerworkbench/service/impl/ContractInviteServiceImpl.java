package com.fpt.producerworkbench.service.impl;

import com.fpt.producerworkbench.common.ContractDocumentType;
import com.fpt.producerworkbench.common.ContractStatus;
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
import com.fpt.producerworkbench.service.ContractInviteService;
import com.fpt.producerworkbench.service.StorageService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.*;
import java.util.Base64;

@Service
@RequiredArgsConstructor
@Slf4j
public class ContractInviteServiceImpl implements ContractInviteService {

    private final ContractRepository contractRepository;
    private final ContractDocumentRepository contractDocumentRepository;
    private final StorageService storageService;
    private final SignNowClient signNowClient;


    private static boolean eqIgnore(String a, String b) {
        return a != null && b != null && a.trim().equalsIgnoreCase(b.trim());
    }

    private static List<ContractInviteRequest.Signer> normalizeSequentialOrders(List<ContractInviteRequest.Signer> signers) {
        int idx = 1;
        List<ContractInviteRequest.Signer> out = new ArrayList<>(signers.size());
        for (var s : signers) {
            var copy = ContractInviteRequest.Signer.builder()
                    .email(s.getEmail())
                    .fullName(s.getFullName())
                    .roleId(s.getRoleId())
                    .roleName(s.getRoleName())
                    .order(idx++)
                    .build();
            out.add(copy);
        }
        return out;
    }

    @Override
    @Transactional
    public StartSigningResponse invite(Authentication auth, Long contractId, ContractInviteRequest req) {
        Contract c = contractRepository.findById(contractId)
                .orElseThrow(() -> new AppException(ErrorCode.CONTRACT_NOT_FOUND));

        if (req.getSigners() == null || req.getSigners().isEmpty()) {
            throw new AppException(ErrorCode.SIGNERS_REQUIRED);
        }
        boolean sequential = req.getSigningOrder() == SigningOrderType.SEQUENTIAL;

        if (c.getSignnowDocumentId() == null) {
            byte[] pdfBytes;
            if (req.getPdfBase64() != null && !req.getPdfBase64().isBlank()) {
                try {
                    pdfBytes = Base64.getDecoder().decode(req.getPdfBase64());
                } catch (IllegalArgumentException e) {
                    throw new AppException(ErrorCode.PDF_BASE64_INVALID);
                }
            } else {
                ContractDocument filled = contractDocumentRepository
                        .findTopByContract_IdAndTypeOrderByVersionDesc(contractId, ContractDocumentType.FILLED)
                        .orElseThrow(() -> new AppException(ErrorCode.CONTRACT_FILLED_PDF_NOT_FOUND));
                try {
                    pdfBytes = storageService.load(filled.getStorageUrl());
                } catch (RuntimeException ex) {
                    throw new AppException(ErrorCode.STORAGE_READ_FAILED);
                }
            }
            try {
                String docId = signNowClient.uploadDocumentWithFieldExtract(pdfBytes, "contract-" + contractId + ".pdf");
                c.setSignnowDocumentId(docId);
            } catch (WebClientResponseException wex) {
                throw new AppException(ErrorCode.SIGNNOW_UPLOAD_FAILED);
            } catch (Exception ex) {
                throw new AppException(ErrorCode.SIGNNOW_UPLOAD_FAILED);
            }
        }

        String ownerEmail = signNowClient.getDocumentOwnerEmail(c.getSignnowDocumentId());
        List<ContractInviteRequest.Signer> inputSigners = Optional.ofNullable(req.getSigners()).orElseGet(List::of);
        List<ContractInviteRequest.Signer> filtered = new ArrayList<>();
        for (var s : inputSigners) {
            if (s.getEmail() == null || s.getEmail().isBlank())
                throw new AppException(ErrorCode.SIGNER_EMAIL_REQUIRED);
            if (eqIgnore(s.getEmail(), ownerEmail)) continue;
            filtered.add(s);
        }
        if (filtered.isEmpty()) {
            throw new AppException(ErrorCode.SIGNERS_REQUIRED);
        }
        if (sequential) {
            filtered = normalizeSequentialOrders(filtered);
        }

        StartSigningResponse resp = new StartSigningResponse();
        try {
            if (Boolean.TRUE.equals(req.getUseFieldInvite())) {
                Map<String, String> roleIdMap = signNowClient.getRoleIdMap(c.getSignnowDocumentId());
                if (roleIdMap.isEmpty()) {
                    throw new AppException(ErrorCode.SIGNNOW_DOC_HAS_NO_FIELDS);
                }
                List<Map<String, Object>> to = new ArrayList<>();
                for (var s : filtered) {
                    String roleId = s.getRoleId();
                    if ((roleId == null || roleId.isBlank()) && s.getRoleName() != null) {
                        roleId = roleIdMap.get(s.getRoleName());
                    }
                    if (roleId == null || roleId.isBlank()) {
                        throw new AppException(ErrorCode.ROLE_ID_REQUIRED);
                    }
                    Map<String, Object> m = new HashMap<>();
                    m.put("email", s.getEmail());
                    m.put("role_id", roleId);
                    if (s.getOrder() != null) m.put("order", s.getOrder());
                    to.add(m);
                }
                Map<String, Object> inv = signNowClient.createFieldInvite(c.getSignnowDocumentId(), to, sequential, null);
                resp.setInviteId((String) inv.getOrDefault("id", "invite"));
            } else {
                List<String> emails = new ArrayList<>();
                for (var s : filtered) emails.add(s.getEmail());
                Map<String, Object> inv = signNowClient.createFreeFormInvite(c.getSignnowDocumentId(), emails, sequential, null);
                resp.setInviteId((String) inv.getOrDefault("id", "invite"));
            }
        } catch (WebClientResponseException wex) {
            throw new AppException(ErrorCode.SIGNNOW_INVITE_FAILED);
        } catch (Exception ex) {
            throw new AppException(ErrorCode.SIGNNOW_INVITE_FAILED);
        }

        c.setStatus(ContractStatus.DRAFT);
        contractRepository.save(c);
        return resp;
    }
}

