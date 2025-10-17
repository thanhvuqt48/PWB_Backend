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
import com.fpt.producerworkbench.service.FileStorageService;
import com.fpt.producerworkbench.service.ContractPermissionService;
import com.fpt.producerworkbench.dto.event.NotificationEvent;
import org.springframework.kafka.core.KafkaTemplate;
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
    private final FileStorageService fileStorageService;
    private final SignNowClient signNowClient;
    private final KafkaTemplate<String, NotificationEvent> kafkaTemplate;
    private final ContractPermissionService contractPermissionService;


    private static boolean eqIgnore(String a, String b) {
        return a != null && b != null && a.trim().equalsIgnoreCase(b.trim());
    }


    /**
     * Tự động tạo danh sách signers từ project client và owner
     */
    private List<ContractInviteRequest.Signer> generateAutoSigners(com.fpt.producerworkbench.entity.Project project, SigningOrderType signingOrder) {
        List<ContractInviteRequest.Signer> signers = new ArrayList<>();
        
        // Kiểm tra project có client không
        if (project.getClient() == null) {
            throw new AppException(ErrorCode.CLIENT_NOT_FOUND);
        }
        
        boolean isSequential = signingOrder == SigningOrderType.SEQUENTIAL;
        
        // Client - ký trước (order = 1)
        ContractInviteRequest.Signer clientSigner = ContractInviteRequest.Signer.builder()
                .email(project.getClient().getEmail())
                .fullName(project.getClient().getFullName())
                .roleName("SignerA")
                .order(1)
                .build();
        signers.add(clientSigner);
        
        // Owner - ký sau (order = 2 nếu sequential, order = 1 nếu parallel)
        ContractInviteRequest.Signer ownerSigner = ContractInviteRequest.Signer.builder()
                .email(project.getCreator().getEmail())
                .fullName(project.getCreator().getFullName())
                .roleName("SignerB")
                .order(isSequential ? 2 : 1)
                .build();
        signers.add(ownerSigner);
        
        log.info("Auto-generated signers for project {}: Client={} (SignerA, order=1), Owner={} (SignerB, order={})", 
                project.getId(), 
                project.getClient().getEmail(), 
                project.getCreator().getEmail(),
                isSequential ? 2 : 1);
        
        return signers;
    }

    @Override
    @Transactional
    public StartSigningResponse invite(Authentication auth, Long contractId, ContractInviteRequest req) {
        Contract c = contractRepository.findById(contractId)
                .orElseThrow(() -> new AppException(ErrorCode.CONTRACT_NOT_FOUND));

        if (c.getStatus() == com.fpt.producerworkbench.common.ContractStatus.COMPLETED
                || c.getSignnowStatus() == com.fpt.producerworkbench.common.ContractStatus.COMPLETED) {
            throw new AppException(ErrorCode.INVITE_NOT_ALLOWED_ALREADY_COMPLETED);
        }
        var existingSigned = contractDocumentRepository
                .findTopByContract_IdAndTypeOrderByVersionDesc(contractId, com.fpt.producerworkbench.common.ContractDocumentType.SIGNED)
                .orElse(null);
        if (existingSigned != null) {
            throw new AppException(ErrorCode.INVITE_NOT_ALLOWED_ALREADY_COMPLETED);
        }

        // Check permissions using service
        var permissions = contractPermissionService.checkContractPermissions(auth, c.getProject().getId());
        if (!permissions.isCanInviteToSign()) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }

        // Auto-generate signers from project client and owner
        List<ContractInviteRequest.Signer> autoSigners = generateAutoSigners(c.getProject(), req.getSigningOrder());
        
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
                    pdfBytes = fileStorageService.downloadBytes(filled.getStorageUrl());
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
        List<ContractInviteRequest.Signer> filtered = new ArrayList<>();
        
        // Use auto-generated signers instead of request signers
        for (var s : autoSigners) {
            if (s.getEmail() == null || s.getEmail().isBlank())
                throw new AppException(ErrorCode.SIGNER_EMAIL_REQUIRED);
            if (eqIgnore(s.getEmail(), ownerEmail)) continue;
            filtered.add(s);
        }
        
        if (filtered.isEmpty()) {
            throw new AppException(ErrorCode.SIGNERS_REQUIRED);
        }

        StartSigningResponse resp = new StartSigningResponse();
        try {
            // Mặc định luôn sử dụng field invite
            if (req.getUseFieldInvite() == null || Boolean.TRUE.equals(req.getUseFieldInvite())) {
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

        c.setStatus(ContractStatus.OUT_FOR_SIGNATURE);
        contractRepository.save(c);

        try {
            String previewUrl = null;
            try {
                var filledDoc = contractDocumentRepository
                        .findTopByContract_IdAndTypeOrderByVersionDesc(contractId, ContractDocumentType.FILLED)
                        .orElse(null);
                if (filledDoc != null) {
                    previewUrl = fileStorageService.generatePresignedUrl(filledDoc.getStorageUrl(), false, null);
                }
            } catch (Exception ignore) { }

            for (var s : filtered) {
                if (s.getEmail() == null || s.getEmail().isBlank()) continue;
                NotificationEvent event = NotificationEvent.builder()
                        .subject("Yêu cầu ký hợp đồng - Project #" + c.getProject().getId())
                        .recipient(s.getEmail().trim())
                        .templateCode("contract-invite-sent.html")
                        .param(new java.util.HashMap<>())
                        .build();
                event.getParam().put("projectId", String.valueOf(c.getProject().getId()));
                event.getParam().put("projectTitle", c.getProject().getTitle());
                event.getParam().put("contractId", String.valueOf(c.getId()));
                event.getParam().put("recipient", s.getFullName() == null ? s.getEmail() : s.getFullName());
                if (previewUrl != null) {
                    event.getParam().put("previewUrl", previewUrl);
                }
                kafkaTemplate.send("notification-delivery", event);
            }
        } catch (Exception mailEx) {
            log.warn("Failed to publish contract invite notification emails: {}", mailEx.getMessage());
        }

        return resp;
    }
}

