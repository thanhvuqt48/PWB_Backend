package com.fpt.producerworkbench.service.impl;

import com.fpt.producerworkbench.common.ContractDocumentType;
import com.fpt.producerworkbench.common.ContractStatus;
import com.fpt.producerworkbench.common.NotificationType;
import com.fpt.producerworkbench.common.RelatedEntityType;
import com.fpt.producerworkbench.configuration.SignNowClient;
import com.fpt.producerworkbench.dto.request.ContractInviteRequest;
import com.fpt.producerworkbench.dto.request.SendNotificationRequest;
import com.fpt.producerworkbench.dto.response.StartSigningResponse;
import com.fpt.producerworkbench.entity.Contract;
import com.fpt.producerworkbench.entity.ContractDocument;
import com.fpt.producerworkbench.exception.AppException;
import com.fpt.producerworkbench.exception.ErrorCode;
import com.fpt.producerworkbench.repository.ContractDocumentRepository;
import com.fpt.producerworkbench.repository.ContractRepository;
import com.fpt.producerworkbench.service.ContractInviteService;
import com.fpt.producerworkbench.service.FileStorageService;
import com.fpt.producerworkbench.service.ProjectPermissionService;
import com.fpt.producerworkbench.dto.event.NotificationEvent;
import com.fpt.producerworkbench.service.SignNowWebhookService;
import com.fpt.producerworkbench.service.NotificationService;
import com.fpt.producerworkbench.repository.UserRepository;
import com.fpt.producerworkbench.entity.User;
import org.springframework.kafka.core.KafkaTemplate;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
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
    private final ProjectPermissionService projectPermissionService;
    private final SignNowWebhookService signNowWebhookService;
    private final NotificationService notificationService;
    private final UserRepository userRepository;
    
    @Lazy
    @Autowired
    private ContractInviteServiceImpl self;


    private static boolean eqIgnore(String a, String b) {
        return a != null && b != null && a.trim().equalsIgnoreCase(b.trim());
    }

    @Async
    public void sendInviteEmailsAsync(Contract contract, List<ContractInviteRequest.Signer> signers) {
        try {
            String previewUrl = null;
            try {
                var filledDoc = contractDocumentRepository
                        .findTopByContract_IdAndTypeOrderByVersionDesc(contract.getId(), ContractDocumentType.FILLED)
                        .orElse(null);
                if (filledDoc != null) {
                    previewUrl = fileStorageService.generatePresignedUrl(filledDoc.getStorageUrl(), false, null);
                }
            } catch (Exception ignore) { }

            for (var s : signers) {
                if (s.getEmail() == null || s.getEmail().isBlank()) continue;
                NotificationEvent event = NotificationEvent.builder()
                        .subject("Yêu cầu ký hợp đồng - Project #" + contract.getProject().getId())
                        .recipient(s.getEmail().trim())
                        .templateCode("contract-invite-sent.html")
                        .param(new java.util.HashMap<>())
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
            log.info("Sent invite emails for contract {} to {} signers", contract.getId(), signers.size());
        } catch (Exception mailEx) {
            log.error("Failed to send invite emails for contract {}: {}", contract.getId(), mailEx.getMessage(), mailEx);
        }
    }



    private List<ContractInviteRequest.Signer> generateAutoSigners(com.fpt.producerworkbench.entity.Project project) {
        List<ContractInviteRequest.Signer> signers = new ArrayList<>();

        if (project.getClient() == null) {
            throw new AppException(ErrorCode.CLIENT_NOT_FOUND);
        }

        ContractInviteRequest.Signer ownerSigner = ContractInviteRequest.Signer.builder()
                .email(project.getCreator().getEmail())
                .fullName(project.getCreator().getFullName())
                .roleName("SignerB")
                .order(1)
                .build();
        signers.add(ownerSigner);

        ContractInviteRequest.Signer clientSigner = ContractInviteRequest.Signer.builder()
                .email(project.getClient().getEmail())
                .fullName(project.getClient().getFullName())
                .roleName("SignerA")
                .order(2)
                .build();
        signers.add(clientSigner);

        log.info("Auto-generated signers for project {}: Owner={} (SignerB, order=1), Client={} (SignerA, order=2)",
                project.getId(),
                project.getCreator().getEmail(),
                project.getClient().getEmail());

        return signers;
    }

    @Override
    @Transactional
    public StartSigningResponse invite(Authentication auth, Long contractId, ContractInviteRequest req) {
        Contract c = contractRepository.findById(contractId)
                .orElseThrow(() -> new AppException(ErrorCode.CONTRACT_NOT_FOUND));

        // Kiểm tra nếu lời mời đã được gửi (trạng thái OUT_FOR_SIGNATURE hoặc PARTIALLY_SIGNED)
        // → Báo "đã gửi rồi, không thể mời ký tiếp"
        if (c.getSignnowStatus() == ContractStatus.OUT_FOR_SIGNATURE || 
            c.getSignnowStatus() == ContractStatus.PARTIALLY_SIGNED) {
            throw new AppException(ErrorCode.INVITE_ALREADY_SENT);
        }

        // Không cho phép gửi lại khi đã ký xong (SIGNED, PAID, COMPLETED)
        // → Báo "hợp đồng đã hoàn tất ký"
        if (c.getSignnowStatus() == ContractStatus.SIGNED || 
            c.getSignnowStatus() == ContractStatus.PAID || 
            c.getSignnowStatus() == ContractStatus.COMPLETED) {
            throw new AppException(ErrorCode.INVITE_NOT_ALLOWED_ALREADY_COMPLETED);
        }

        var permissions = projectPermissionService.checkContractPermissions(auth, c.getProject().getId());
        if (!permissions.isCanInviteToSign()) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }

        // Nếu signnowStatus là DRAFT (hợp đồng mới hoặc đã reset), đảm bảo reset signnowDocumentId
        // để upload document mới, tránh dùng document cũ có thể đã có invite
        if (c.getSignnowStatus() == com.fpt.producerworkbench.common.ContractStatus.DRAFT && c.getSignnowDocumentId() != null) {
            log.info("Resetting signnowDocumentId for DRAFT contract {} to allow new document upload", c.getId());
            c.setSignnowDocumentId(null);
            c.setSignnowStatus(com.fpt.producerworkbench.common.ContractStatus.DRAFT);
            contractRepository.save(c);
        }

        List<ContractInviteRequest.Signer> autoSigners = generateAutoSigners(c.getProject());

        boolean sequential = true;

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

            String docId;
            try {
                docId = signNowClient.uploadDocumentWithFieldExtract(pdfBytes, "contract-" + contractId + ".pdf");
            } catch (WebClientResponseException ex) {
                int sc = ex.getStatusCode().value();
                log.error("[ContractInvite] Upload document failed: status={} body={}", sc, ex.getResponseBodyAsString());
                if (sc == 404) {
                    throw new AppException(ErrorCode.SIGNNOW_DOC_ID_NOT_FOUND);
                }
                throw new AppException(ErrorCode.SIGNNOW_UPLOAD_FAILED);
            } catch (Exception ex) {
                log.error("[ContractInvite] Upload document failed (unexpected): {}", ex.getMessage(), ex);
                throw new AppException(ErrorCode.SIGNNOW_UPLOAD_FAILED);
            }
            
            if (docId == null || docId.isBlank()) {
                log.error("[ContractInvite] Upload document returned null or empty document ID");
                throw new AppException(ErrorCode.SIGNNOW_UPLOAD_FAILED);
            }
            
            c.setSignnowDocumentId(docId);
            // Save contract ngay sau khi set signnowDocumentId để đảm bảo webhook có thể tìm thấy contract
            // khi document.complete event được gửi từ SignNow
            contractRepository.save(c);
            
            // Đăng ký webhook async để không block request
            signNowWebhookService.ensureCompletedEventForDocument(docId);
        }

        String ownerEmail;
        try {
            ownerEmail = signNowClient.getDocumentOwnerEmail(c.getSignnowDocumentId());
        } catch (WebClientResponseException ex) {
            int sc = ex.getStatusCode().value();
            log.error("[ContractInvite] Get document owner email failed: status={} body={}", sc, ex.getResponseBodyAsString());
            if (sc == 404) {
                throw new AppException(ErrorCode.SIGNNOW_DOC_ID_NOT_FOUND);
            }
            throw new AppException(ErrorCode.SIGNNOW_DOWNLOAD_FAILED);
        } catch (Exception ex) {
            log.error("[ContractInvite] Get document owner email failed (unexpected): {}", ex.getMessage(), ex);
            throw new AppException(ErrorCode.SIGNNOW_DOWNLOAD_FAILED);
        }
        List<ContractInviteRequest.Signer> filtered = new ArrayList<>();

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
            if (req.getUseFieldInvite() == null || Boolean.TRUE.equals(req.getUseFieldInvite())) {
                Map<String, String> roleIdMap;
                try {
                    roleIdMap = signNowClient.getRoleIdMap(c.getSignnowDocumentId());
                } catch (WebClientResponseException ex) {
                    int sc = ex.getStatusCode().value();
                    log.error("[ContractInvite] Get role ID map failed: status={} body={}", sc, ex.getResponseBodyAsString());
                    if (sc == 404) {
                        throw new AppException(ErrorCode.SIGNNOW_DOC_ID_NOT_FOUND);
                    }
                    throw new AppException(ErrorCode.SIGNNOW_DOC_HAS_NO_FIELDS);
                } catch (Exception ex) {
                    log.error("[ContractInvite] Get role ID map failed (unexpected): {}", ex.getMessage(), ex);
                    throw new AppException(ErrorCode.SIGNNOW_DOC_HAS_NO_FIELDS);
                }
                
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
        } catch (AppException ex) {
            // Re-throw AppException để giữ nguyên mã lỗi cụ thể
            throw ex;
        } catch (WebClientResponseException wex) {
            int sc = wex.getStatusCode().value();
            log.error("[ContractInvite] Create invite failed: status={} body={}", sc, wex.getResponseBodyAsString());
            if (sc == 404) {
                throw new AppException(ErrorCode.SIGNNOW_DOC_ID_NOT_FOUND);
            } else if (sc == 400) {
                throw new AppException(ErrorCode.SIGNNOW_DOC_HAS_NO_FIELDS);
            } else if (sc == 422) {
                // Validation error từ SignNow
                throw new AppException(ErrorCode.SIGNNOW_INVITE_FAILED);
            } else {
                throw new AppException(ErrorCode.SIGNNOW_INVITE_FAILED);
            }
        } catch (Exception ex) {
            log.error("[ContractInvite] Create invite failed (unexpected): {}", ex.getMessage(), ex);
            throw new AppException(ErrorCode.SIGNNOW_INVITE_FAILED);
        }

        c.setSignnowStatus(ContractStatus.OUT_FOR_SIGNATURE);
        contractRepository.save(c);

        // Gửi mail async để không block request (gọi qua self để Spring proxy hoạt động)
        self.sendInviteEmailsAsync(c, filtered);

        // Gửi notification realtime chỉ cho khách hàng (không gửi cho người dùng hiện tại)
        try {
            User currentUser = userRepository.findByEmail(auth.getName())
                    .orElse(null);
            String inviterName = currentUser != null 
                    ? (currentUser.getFullName() != null ? currentUser.getFullName() : currentUser.getEmail())
                    : "Hệ thống";
            
            // Lấy email của khách hàng từ project
            String clientEmail = c.getProject().getClient() != null 
                    ? c.getProject().getClient().getEmail() 
                    : null;
            
            if (clientEmail != null && !clientEmail.isBlank()) {
                userRepository.findByEmail(clientEmail).ifPresent(user -> {
                    String actionUrl = String.format("/contractId=%d", c.getProject().getId());
                    
                    notificationService.sendNotification(
                            SendNotificationRequest.builder()
                                    .userId(user.getId())
                                    .type(NotificationType.CONTRACT_SIGNING)
                                    .title("Yêu cầu ký hợp đồng")
                                    .message(String.format("%s đã gửi yêu cầu ký hợp đồng cho dự án ở email \"%s\". Vui lòng ký hợp đồng để tiếp tục.",
                                            inviterName,
                                            c.getProject().getTitle()))
                                    .relatedEntityType(RelatedEntityType.CONTRACT)
                                    .relatedEntityId(c.getId())
                                    .actionUrl(actionUrl)
                                    .build()
                    );
                });
            }
        } catch (Exception e) {
            log.error("Gặp lỗi khi gửi notification realtime cho contract signing: {}", e.getMessage());
        }

        return resp;
    }
}
