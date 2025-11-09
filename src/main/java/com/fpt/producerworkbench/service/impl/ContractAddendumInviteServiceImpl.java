package com.fpt.producerworkbench.service.impl;

import com.fpt.producerworkbench.common.ContractDocumentType;
import com.fpt.producerworkbench.common.ContractStatus;
import com.fpt.producerworkbench.configuration.SignNowClient;
import com.fpt.producerworkbench.dto.request.ContractInviteRequest;
import com.fpt.producerworkbench.dto.response.StartSigningResponse;
import com.fpt.producerworkbench.entity.Contract;
import com.fpt.producerworkbench.entity.ContractAddendum;
import com.fpt.producerworkbench.exception.AppException;
import com.fpt.producerworkbench.exception.ErrorCode;
import com.fpt.producerworkbench.repository.ContractAddendumRepository;
import com.fpt.producerworkbench.repository.ContractDocumentRepository;
import com.fpt.producerworkbench.repository.ContractRepository;
import com.fpt.producerworkbench.service.ContractAddendumInviteService;
import com.fpt.producerworkbench.service.ProjectPermissionService;
import com.fpt.producerworkbench.service.FileStorageService;
import com.fpt.producerworkbench.service.SignNowWebhookService;
import com.fpt.producerworkbench.dto.event.NotificationEvent;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class ContractAddendumInviteServiceImpl implements ContractAddendumInviteService {

    private final ContractRepository contractRepository;
    private final ContractAddendumRepository addendumRepository;
    private final ContractDocumentRepository contractDocumentRepository;
    private final FileStorageService fileStorageService;
    private final SignNowClient signNowClient;
    private final SignNowWebhookService signNowWebhookService;
    private final KafkaTemplate<String, NotificationEvent> kafkaTemplate;
    private final ProjectPermissionService projectPermissionService;

    private static boolean eqIgnore(String a, String b) {
        return a != null && b != null && a.trim().equalsIgnoreCase(b.trim());
    }

    private List<ContractInviteRequest.Signer> generateAutoSigners(Contract c) {
        var project = c.getProject();
        if (project.getClient() == null) throw new AppException(ErrorCode.CLIENT_NOT_FOUND);

        List<ContractInviteRequest.Signer> signers = new ArrayList<>();
        signers.add(ContractInviteRequest.Signer.builder()
                .email(project.getCreator().getEmail())
                .fullName(project.getCreator().getFullName())
                .roleName("SignerB").order(1).build());
        signers.add(ContractInviteRequest.Signer.builder()
                .email(project.getClient().getEmail())
                .fullName(project.getClient().getFullName())
                .roleName("SignerA").order(2).build());

        log.info("[Addendum] Auto signers: owner={}, client={}",
                project.getCreator().getEmail(), project.getClient().getEmail());
        return signers;
    }

    @Override
    @Transactional
    public StartSigningResponse inviteAddendum(Authentication auth, Long contractId, ContractInviteRequest req) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new AppException(ErrorCode.CONTRACT_NOT_FOUND));

        var permissions = projectPermissionService.checkContractPermissions(auth, contract.getProject().getId());
        if (!permissions.isCanInviteToSign()) throw new AppException(ErrorCode.ACCESS_DENIED);

        ContractAddendum addendum = addendumRepository
                .findFirstByContractIdOrderByVersionDesc(contractId)
                .orElseThrow(() -> new AppException(ErrorCode.BAD_REQUEST));

        if (addendum.getSignnowStatus() == ContractStatus.COMPLETED) {
            throw new AppException(ErrorCode.INVITE_NOT_ALLOWED_ALREADY_COMPLETED);
        }

        byte[] pdfBytes;
        try {
            var addDoc = contractDocumentRepository
                    .findFirstByContractIdAndTypeOrderByVersionDesc(contractId, ContractDocumentType.ADDENDUM)
                    .orElseThrow(() -> new AppException(ErrorCode.CONTRACT_FILLED_PDF_NOT_FOUND));
            pdfBytes = fileStorageService.downloadBytes(addDoc.getStorageUrl());
        } catch (RuntimeException ex) {
            throw new AppException(ErrorCode.STORAGE_READ_FAILED);
        }

        if (addendum.getSignnowDocumentId() == null) {
            String docId = signNowClient.uploadDocumentWithFieldExtract(pdfBytes, "addendum-" + contractId + ".pdf");
            addendum.setSignnowDocumentId(docId);

            try {
                signNowWebhookService.ensureCompletedEventForDocument(docId);
            } catch (WebClientResponseException ex) {
                int sc = ex.getStatusCode().value();
                log.warn("[Addendum][Webhook] register failed: status={} body={}", sc, ex.getResponseBodyAsString());
            } catch (Exception ex) {
                log.warn("[Addendum][Webhook] register failed (unexpected): {}", ex.toString());
            }
        }

        List<ContractInviteRequest.Signer> auto = generateAutoSigners(contract);
        String ownerEmail = signNowClient.getDocumentOwnerEmail(addendum.getSignnowDocumentId());
        List<ContractInviteRequest.Signer> filtered = new ArrayList<>();
        for (var s : auto) {
            if (s.getEmail() == null || s.getEmail().isBlank())
                throw new AppException(ErrorCode.SIGNER_EMAIL_REQUIRED);
            if (eqIgnore(s.getEmail(), ownerEmail)) continue;
            filtered.add(s);
        }
        if (filtered.isEmpty()) throw new AppException(ErrorCode.SIGNERS_REQUIRED);

        boolean sequential = true;
        StartSigningResponse resp = new StartSigningResponse();
        try {
            if (req.getUseFieldInvite() == null || Boolean.TRUE.equals(req.getUseFieldInvite())) {
                Map<String, String> roleIdMap = signNowClient.getRoleIdMap(addendum.getSignnowDocumentId());
                if (roleIdMap.isEmpty()) throw new AppException(ErrorCode.SIGNNOW_DOC_HAS_NO_FIELDS);

                List<Map<String, Object>> to = new ArrayList<>();
                for (var s : filtered) {
                    String roleId = s.getRoleId();
                    if ((roleId == null || roleId.isBlank()) && s.getRoleName() != null) {
                        roleId = roleIdMap.get(s.getRoleName());
                    }
                    if (roleId == null || roleId.isBlank()) throw new AppException(ErrorCode.ROLE_ID_REQUIRED);
                    Map<String, Object> m = new HashMap<>();
                    m.put("email", s.getEmail());
                    m.put("role_id", roleId);
                    if (s.getOrder() != null) m.put("order", s.getOrder());
                    to.add(m);
                }
                Map<String, Object> inv = signNowClient.createFieldInvite(addendum.getSignnowDocumentId(), to, sequential, null);
                resp.setInviteId((String) inv.getOrDefault("id", "invite"));
            } else {
                List<String> emails = new ArrayList<>();
                for (var s : filtered) emails.add(s.getEmail());
                Map<String, Object> inv = signNowClient.createFreeFormInvite(addendum.getSignnowDocumentId(), emails, sequential, null);
                resp.setInviteId((String) inv.getOrDefault("id", "invite"));
            }
        } catch (WebClientResponseException wex) {
            throw new AppException(ErrorCode.SIGNNOW_INVITE_FAILED);
        } catch (Exception ex) {
            throw new AppException(ErrorCode.SIGNNOW_INVITE_FAILED);
        }

        addendum.setSignnowStatus(ContractStatus.OUT_FOR_SIGNATURE);
        addendumRepository.save(addendum);

        try {
            String previewUrl = null;
            try {
                var addDoc = contractDocumentRepository
                        .findFirstByContractIdAndTypeOrderByVersionDesc(contractId, ContractDocumentType.ADDENDUM)
                        .orElse(null);
                if (addDoc != null) {
                    previewUrl = fileStorageService.generatePresignedUrl(addDoc.getStorageUrl(), false, null);
                }
            } catch (Exception ignore) { }

            for (var s : filtered) {
                if (s.getEmail() == null || s.getEmail().isBlank()) continue;
                NotificationEvent event = NotificationEvent.builder()
                        .subject("Yêu cầu ký phụ lục - Project #" + contract.getProject().getId())
                        .recipient(s.getEmail().trim())
                        .templateCode("contract-addendum-invite-sent.html")
                        .param(new HashMap<>())
                        .build();
                event.getParam().put("projectId", String.valueOf(contract.getProject().getId()));
                event.getParam().put("projectTitle", contract.getProject().getTitle());
                event.getParam().put("contractId", String.valueOf(contract.getId()));
                if (previewUrl != null) event.getParam().put("previewUrl", previewUrl);
                kafkaTemplate.send("notification-delivery", event);
            }
        } catch (Exception mailEx) {
            log.warn("[Addendum] publish invite emails failed: {}", mailEx.getMessage());
        }

        return resp;
    }
}
