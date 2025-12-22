package com.fpt.producerworkbench.service.impl;

import com.fpt.producerworkbench.common.AddendumDocumentType;
import com.fpt.producerworkbench.common.ContractStatus;
import com.fpt.producerworkbench.configuration.SignNowClient;
import com.fpt.producerworkbench.dto.request.ContractInviteRequest;
import com.fpt.producerworkbench.dto.response.StartSigningResponse;
import com.fpt.producerworkbench.entity.Contract;
import com.fpt.producerworkbench.entity.ContractAddendum;
import com.fpt.producerworkbench.exception.AppException;
import com.fpt.producerworkbench.exception.ErrorCode;
import com.fpt.producerworkbench.repository.AddendumDocumentRepository;
import com.fpt.producerworkbench.repository.ContractAddendumRepository;
import com.fpt.producerworkbench.repository.ContractRepository;
import com.fpt.producerworkbench.service.ContractAddendumInviteService;
import com.fpt.producerworkbench.service.ProjectPermissionService;
import com.fpt.producerworkbench.service.FileStorageService;
import com.fpt.producerworkbench.service.SignNowWebhookService;
import com.fpt.producerworkbench.service.NotificationService;
import com.fpt.producerworkbench.dto.event.NotificationEvent;
import com.fpt.producerworkbench.dto.request.SendNotificationRequest;
import com.fpt.producerworkbench.common.NotificationType;
import com.fpt.producerworkbench.common.RelatedEntityType;
import com.fpt.producerworkbench.repository.UserRepository;
import com.fpt.producerworkbench.entity.User;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
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
    private final AddendumDocumentRepository addendumDocumentRepository;
    private final FileStorageService fileStorageService;
    private final SignNowClient signNowClient;
    private final SignNowWebhookService signNowWebhookService;
    private final KafkaTemplate<String, NotificationEvent> kafkaTemplate;
    private final ProjectPermissionService projectPermissionService;
    private final NotificationService notificationService;
    private final UserRepository userRepository;

    @Lazy
    @Autowired
    private ContractAddendumInviteServiceImpl self;

    private static boolean eqIgnore(String a, String b) {
        return a != null && b != null && a.trim().equalsIgnoreCase(b.trim());
    }

    private List<ContractInviteRequest.Signer> generateAutoSigners(Contract c) {
        var project = c.getProject();
        if (project.getClient() == null)
            throw new AppException(ErrorCode.CLIENT_NOT_FOUND);

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

    @Async
    public void sendInviteEmailsAsync(Contract contract, ContractAddendum addendum,
                                      List<ContractInviteRequest.Signer> signers) {
        try {
            String previewUrl = null;
            try {
                var addDoc = addendumDocumentRepository
                        .findFirstByAddendumIdAndTypeOrderByVersionDesc(addendum.getId(), AddendumDocumentType.FILLED)
                        .orElse(null);
                if (addDoc != null) {
                    previewUrl = fileStorageService.generatePresignedUrl(addDoc.getStorageUrl(), false, null);
                }
            } catch (Exception ignore) {
            }

            for (var s : signers) {
                if (s.getEmail() == null || s.getEmail().isBlank())
                    continue;
                NotificationEvent event = NotificationEvent.builder()
                        .subject("Yêu cầu ký phụ lục - Dự án #" + contract.getProject().getId())
                        .recipient(s.getEmail().trim())
                        .templateCode("contract-addendum-invite-sent.html")
                        .param(new HashMap<>())
                        .build();
                event.getParam().put("projectId", String.valueOf(contract.getProject().getId()));
                event.getParam().put("projectTitle", contract.getProject().getTitle());
                event.getParam().put("contractId", String.valueOf(contract.getId()));
                event.getParam().put("recipient", s.getFullName() == null ? s.getEmail() : s.getFullName());
                if (previewUrl != null) {
                    event.getParam().put("previewUrl", previewUrl);
                }
                kafkaTemplate.send("notification-delivery", event);
            }
            log.info("[Addendum] Sent invite emails for contract {} to {} signers", contract.getId(), signers.size());
        } catch (Exception mailEx) {
            log.error("[Addendum] Failed to send invite emails for contract {}: {}", contract.getId(),
                    mailEx.getMessage(), mailEx);
        }
    }

    @Override
    @Transactional
    public StartSigningResponse inviteAddendum(Authentication auth, Long contractId, ContractInviteRequest req) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new AppException(ErrorCode.CONTRACT_NOT_FOUND));

        var permissions = projectPermissionService.checkContractPermissions(auth, contract.getProject().getId());
        if (!permissions.isCanInviteToSign())
            throw new AppException(ErrorCode.ACCESS_DENIED);

        ContractAddendum addendum = addendumRepository
                .findFirstByContractIdOrderByAddendumNumberDescVersionDesc(contractId)
                .orElseThrow(() -> new AppException(ErrorCode.BAD_REQUEST));

        if (addendum.getSignnowStatus() == ContractStatus.OUT_FOR_SIGNATURE ||
                addendum.getSignnowStatus() == ContractStatus.PARTIALLY_SIGNED) {
            throw new AppException(ErrorCode.INVITE_ALREADY_SENT);
        }

        if (addendum.getSignnowStatus() == ContractStatus.SIGNED ||
                addendum.getSignnowStatus() == ContractStatus.PAID ||
                addendum.getSignnowStatus() == ContractStatus.COMPLETED) {
            throw new AppException(ErrorCode.INVITE_NOT_ALLOWED_ALREADY_COMPLETED);
        }

        if (addendum.getSignnowStatus() == ContractStatus.DRAFT
                && addendum.getSignnowDocumentId() != null
                && !addendum.getSignnowDocumentId().isBlank()) {
            log.info("[Addendum] Resetting signnowDocumentId for DRAFT addendum {} to allow new document upload",
                    addendum.getId());
            addendum.setSignnowDocumentId(null);
            addendumRepository.save(addendum);
        }

        byte[] pdfBytes;
        try {
            var addDoc = addendumDocumentRepository
                    .findFirstByAddendumIdAndTypeOrderByVersionDesc(addendum.getId(), AddendumDocumentType.FILLED)
                    .orElseThrow(() -> new AppException(ErrorCode.CONTRACT_FILLED_PDF_NOT_FOUND));
            pdfBytes = fileStorageService.downloadBytes(addDoc.getStorageUrl());
        } catch (RuntimeException ex) {
            throw new AppException(ErrorCode.STORAGE_READ_FAILED);
        }

        if (addendum.getSignnowDocumentId() == null) {
            String docId;
            try {
                docId = signNowClient.uploadDocumentWithFieldExtract(pdfBytes, "addendum-" + contractId + ".pdf");
            } catch (WebClientResponseException ex) {
                int sc = ex.getStatusCode().value();
                log.error("[Addendum] Upload document failed: status={} body={}", sc, ex.getResponseBodyAsString());
                if (sc == 404) {
                    throw new AppException(ErrorCode.SIGNNOW_DOC_ID_NOT_FOUND);
                }
                throw new AppException(ErrorCode.SIGNNOW_UPLOAD_FAILED);
            } catch (Exception ex) {
                log.error("[Addendum] Upload document failed (unexpected): {}", ex.getMessage(), ex);
                throw new AppException(ErrorCode.SIGNNOW_UPLOAD_FAILED);
            }

            if (docId == null || docId.isBlank()) {
                log.error("[Addendum] Upload document returned null or empty document ID");
                throw new AppException(ErrorCode.SIGNNOW_UPLOAD_FAILED);
            }

            addendum.setSignnowDocumentId(docId);
            addendumRepository.save(addendum);

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
        String ownerEmail;
        try {
            ownerEmail = signNowClient.getDocumentOwnerEmail(addendum.getSignnowDocumentId());
        } catch (WebClientResponseException ex) {
            int sc = ex.getStatusCode().value();
            log.error("[Addendum] Get document owner email failed: status={} body={}", sc,
                    ex.getResponseBodyAsString());
            if (sc == 404) {
                throw new AppException(ErrorCode.SIGNNOW_DOC_ID_NOT_FOUND);
            }
            throw new AppException(ErrorCode.SIGNNOW_DOWNLOAD_FAILED);
        } catch (Exception ex) {
            log.error("[Addendum] Get document owner email failed (unexpected): {}", ex.getMessage(), ex);
            throw new AppException(ErrorCode.SIGNNOW_DOWNLOAD_FAILED);
        }
        List<ContractInviteRequest.Signer> filtered = new ArrayList<>();
        for (var s : auto) {
            if (s.getEmail() == null || s.getEmail().isBlank())
                throw new AppException(ErrorCode.SIGNER_EMAIL_REQUIRED);
            if (eqIgnore(s.getEmail(), ownerEmail))
                continue;
            filtered.add(s);
        }
        if (filtered.isEmpty())
            throw new AppException(ErrorCode.SIGNERS_REQUIRED);

        boolean sequential = true;
        StartSigningResponse resp = new StartSigningResponse();
        try {
            if (req.getUseFieldInvite() == null || Boolean.TRUE.equals(req.getUseFieldInvite())) {
                Map<String, String> roleIdMap;
                try {
                    roleIdMap = signNowClient.getRoleIdMap(addendum.getSignnowDocumentId());
                } catch (WebClientResponseException ex) {
                    int sc = ex.getStatusCode().value();
                    log.error("[Addendum] Get role ID map failed: status={} body={}", sc, ex.getResponseBodyAsString());
                    if (sc == 404) {
                        throw new AppException(ErrorCode.SIGNNOW_DOC_ID_NOT_FOUND);
                    }
                    throw new AppException(ErrorCode.SIGNNOW_DOC_HAS_NO_FIELDS);
                } catch (Exception ex) {
                    log.error("[Addendum] Get role ID map failed (unexpected): {}", ex.getMessage(), ex);
                    throw new AppException(ErrorCode.SIGNNOW_DOC_HAS_NO_FIELDS);
                }

                if (roleIdMap.isEmpty())
                    throw new AppException(ErrorCode.SIGNNOW_DOC_HAS_NO_FIELDS);

                List<Map<String, Object>> to = new ArrayList<>();
                for (var s : filtered) {
                    String roleId = s.getRoleId();
                    if ((roleId == null || roleId.isBlank()) && s.getRoleName() != null) {
                        roleId = roleIdMap.get(s.getRoleName());
                    }
                    if (roleId == null || roleId.isBlank())
                        throw new AppException(ErrorCode.ROLE_ID_REQUIRED);
                    Map<String, Object> m = new HashMap<>();
                    m.put("email", s.getEmail());
                    m.put("role_id", roleId);
                    if (s.getOrder() != null)
                        m.put("order", s.getOrder());
                    to.add(m);
                }
                Map<String, Object> inv = signNowClient.createFieldInvite(addendum.getSignnowDocumentId(), to,
                        sequential, null);
                resp.setInviteId((String) inv.getOrDefault("id", "invite"));
            } else {
                List<String> emails = new ArrayList<>();
                for (var s : filtered)
                    emails.add(s.getEmail());
                Map<String, Object> inv = signNowClient.createFreeFormInvite(addendum.getSignnowDocumentId(), emails,
                        sequential, null);
                resp.setInviteId((String) inv.getOrDefault("id", "invite"));
            }
        } catch (AppException ex) {
            throw ex;
        } catch (WebClientResponseException wex) {
            int sc = wex.getStatusCode().value();
            log.error("[Addendum] Create invite failed: status={} body={}", sc, wex.getResponseBodyAsString());
            if (sc == 404) {
                throw new AppException(ErrorCode.SIGNNOW_DOC_ID_NOT_FOUND);
            } else if (sc == 400) {
                throw new AppException(ErrorCode.SIGNNOW_DOC_HAS_NO_FIELDS);
            } else if (sc == 422) {
                throw new AppException(ErrorCode.SIGNNOW_INVITE_FAILED);
            } else {
                throw new AppException(ErrorCode.SIGNNOW_INVITE_FAILED);
            }
        } catch (Exception ex) {
            log.error("[Addendum] Create invite failed (unexpected): {}", ex.getMessage(), ex);
            throw new AppException(ErrorCode.SIGNNOW_INVITE_FAILED);
        }

        addendum.setSignnowStatus(ContractStatus.OUT_FOR_SIGNATURE);
        addendumRepository.save(addendum);

        self.sendInviteEmailsAsync(contract, addendum, filtered);

        try {
            User currentUser = userRepository.findByEmail(auth.getName())
                    .orElse(null);
            String inviterName = currentUser != null
                    ? (currentUser.getFullName() != null ? currentUser.getFullName() : currentUser.getEmail())
                    : "Hệ thống";

            String clientEmail = contract.getProject().getClient() != null
                    ? contract.getProject().getClient().getEmail()
                    : null;

            if (clientEmail != null && !clientEmail.isBlank()) {
                userRepository.findByEmail(clientEmail).ifPresent(user -> {
                    String actionUrl = String.format("/addendum-space?id=%d&contractId=%d&addendumNumber=%d", contract.getProject().getId(),
                            contract.getId(), addendum.getId());

                    notificationService.sendNotification(
                            SendNotificationRequest.builder()
                                    .userId(user.getId())
                                    .type(NotificationType.CONTRACT_SIGNING)
                                    .title("Yêu cầu ký phụ lục hợp đồng")
                                    .message(String.format(
                                            "%s đã gửi yêu cầu ký phụ lục cho dự án ở email \"%s\". Vui lòng ký phụ lục để tiếp tục.",
                                            inviterName,
                                            contract.getProject().getTitle()))
                                    .relatedEntityType(RelatedEntityType.CONTRACT)
                                    .relatedEntityId(contract.getId())
                                    .actionUrl(actionUrl)
                                    .build());
                });
            }
        } catch (Exception e) {
            log.error("[Addendum] Gặp lỗi khi gửi notification realtime cho addendum signing: {}", e.getMessage());
        }

        return resp;
    }
}
