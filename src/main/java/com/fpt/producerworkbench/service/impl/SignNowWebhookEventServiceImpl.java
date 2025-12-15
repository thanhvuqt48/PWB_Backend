package com.fpt.producerworkbench.service.impl;

import com.fpt.producerworkbench.common.AddendumDocumentType;
import com.fpt.producerworkbench.common.ContractDocumentType;
import com.fpt.producerworkbench.common.ContractStatus;
import com.fpt.producerworkbench.common.NotificationType;
import com.fpt.producerworkbench.common.RelatedEntityType;
import com.fpt.producerworkbench.configuration.FrontendProperties;
import com.fpt.producerworkbench.configuration.SignNowClient;
import com.fpt.producerworkbench.configuration.SignNowProperties;
import com.fpt.producerworkbench.dto.event.NotificationEvent;
import com.fpt.producerworkbench.dto.request.SendNotificationRequest;
import com.fpt.producerworkbench.entity.AddendumDocument;
import com.fpt.producerworkbench.entity.Contract;
import com.fpt.producerworkbench.entity.ContractAddendum;
import com.fpt.producerworkbench.entity.ContractDocument;
import com.fpt.producerworkbench.entity.Project;
import com.fpt.producerworkbench.entity.User;
import com.fpt.producerworkbench.exception.AppException;
import com.fpt.producerworkbench.exception.ErrorCode;
import com.fpt.producerworkbench.repository.AddendumDocumentRepository;
import com.fpt.producerworkbench.repository.ContractAddendumRepository;
import com.fpt.producerworkbench.repository.ContractDocumentRepository;
import com.fpt.producerworkbench.repository.ContractRepository;
import com.fpt.producerworkbench.service.FileKeyGenerator;
import com.fpt.producerworkbench.service.FileStorageService;
import com.fpt.producerworkbench.service.NotificationService;
import com.fpt.producerworkbench.service.SignNowWebhookEventService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class SignNowWebhookEventServiceImpl implements SignNowWebhookEventService {

    private final ContractRepository contractRepository;
    private final ContractAddendumRepository contractAddendumRepository;
    private final ContractDocumentRepository contractDocumentRepository;
    private final AddendumDocumentRepository addendumDocumentRepository;
    private final SignNowClient signNowClient;
    private final FileStorageService fileStorageService;
    private final FileKeyGenerator fileKeyGenerator;
    private final SignNowProperties props;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, NotificationEvent> kafkaTemplate;
    private final FrontendProperties frontendProperties;
    
    private static final String NOTIFICATION_TOPIC = "notification-delivery";

    @Override
    @Transactional
    public ResponseEntity<String> handleContractEvent(
            Contract contract,
            String documentId,
            String event,
            boolean isFieldInviteComplete,
            boolean isDocumentComplete,
            boolean isSignedEvent
    ) {
        if (isCompleted(contract.getSignnowStatus())) {
            return ResponseEntity.ok("already completed");
        }

        if (isDocumentComplete) {
            try {
                Map<String, Integer> signingStatus = signNowClient.getSigningStatus(documentId);
                int totalSigners = signingStatus.getOrDefault("totalSigners", 0);
                int signedCount = signingStatus.getOrDefault("signedCount", 0);
                int isCompleted = signingStatus.getOrDefault("isCompleted", 0);

                log.info("[Webhook] Contract {} document.complete event - signing status: {}/{} signed, completed={}", 
                        contract.getId(), signedCount, totalSigners, isCompleted);

                if (signedCount >= totalSigners && totalSigners > 0) {
                    if (!isCompleted(contract.getSignnowStatus())) {
                        contract.setSignnowStatus(ContractStatus.SIGNED);
                        contractRepository.save(contract);
                        log.info("[Webhook] Contract {} set to SIGNED (document.complete event - all signers signed: {}/{})", 
                                contract.getId(), signedCount, totalSigners);
                    }
                    boolean withHistory = props.getWebhook().isWithHistory();
                    processSignedPdf(contract.getId(), documentId, 1, true, withHistory);
                    
                    // Gửi thông báo và email cho owner khi tất cả đã ký (client đã ký)
                    sendNotificationToOwnerWhenAllSigned(contract);
                } else if (totalSigners > 0 && signedCount < totalSigners) {
                    log.warn("[Webhook] Contract {} document.complete but API shows {}/{} signed (race condition), still setting SIGNED", 
                            contract.getId(), signedCount, totalSigners);
                    if (!isCompleted(contract.getSignnowStatus())) {
                        contract.setSignnowStatus(ContractStatus.SIGNED);
                        contractRepository.save(contract);
                        log.info("[Webhook] Contract {} set to SIGNED (document.complete event - trusting event despite API delay)", 
                                contract.getId());
                    }
                    try {
                        boolean withHistory = props.getWebhook().isWithHistory();
                        processSignedPdf(contract.getId(), documentId, 1, true, withHistory);
                        // Gửi thông báo và email cho owner khi tất cả đã ký (client đã ký)
                        sendNotificationToOwnerWhenAllSigned(contract);
                    } catch (Exception e) {
                        log.warn("[Webhook] Contract {} PDF download failed (will retry later): {}", contract.getId(), e.getMessage());
                    }
                } else {
                    log.warn("[Webhook] Contract {} document.complete but totalSigners=0, setting SIGNED anyway", 
                            contract.getId());
                    if (!isCompleted(contract.getSignnowStatus())) {
                        contract.setSignnowStatus(ContractStatus.SIGNED);
                        contractRepository.save(contract);
                        log.info("[Webhook] Contract {} set to SIGNED (document.complete event - trusting event)", 
                                contract.getId());
                    }
                }
                
                return ResponseEntity.ok("ok");
            } catch (AppException ex) {
                log.warn("[Webhook] Contract {} download/status check failed: {}", 
                        contract.getId(), ex.getMessage());
                return ResponseEntity.accepted().body("not ready");
            } catch (Exception ex) {
                log.error("[Webhook] Contract {} processSignedPdf failed", contract.getId(), ex);
                return ResponseEntity.internalServerError().body("error");
            }
        }

        if (isFieldInviteComplete || isSignedEvent) {
            try {
                updateContractSigningStatus(contract, documentId);
                return ResponseEntity.ok("status updated");
            } catch (Exception ex) {
                log.error("[Webhook] updateContractSigningStatus failed for contract {}", contract.getId(), ex);
                if (contract.getSignnowStatus() == ContractStatus.OUT_FOR_SIGNATURE) {
                    contract.setSignnowStatus(ContractStatus.PARTIALLY_SIGNED);
                    contractRepository.save(contract);
                    log.warn("[Webhook] Contract {} set to PARTIALLY_SIGNED due to exception in updateContractSigningStatus", 
                            contract.getId());
                }
                return ResponseEntity.ok("status update failed but updated");
            }
        }

        return ResponseEntity.ok("ignored");
    }

    @Override
    @Transactional
    public ResponseEntity<String> handleAddendumEvent(
            ContractAddendum addendum,
            String documentId,
            String event,
            boolean isFieldInviteComplete,
            boolean isDocumentComplete,
            boolean isSignedEvent
    ) {
        if (isCompleted(addendum.getSignnowStatus())) {
            return ResponseEntity.ok("addendum already completed");
        }

        if (isDocumentComplete) {
            try {
                Map<String, Integer> signingStatus = signNowClient.getSigningStatus(documentId);
                int totalSigners = signingStatus.getOrDefault("totalSigners", 0);
                int signedCount = signingStatus.getOrDefault("signedCount", 0);
                int isCompleted = signingStatus.getOrDefault("isCompleted", 0);

                log.info("[Webhook] Addendum {} document.complete event - signing status: {}/{} signed, completed={}", 
                        addendum.getId(), signedCount, totalSigners, isCompleted);

                if (signedCount >= totalSigners && totalSigners > 0) {
                    if (!isCompleted(addendum.getSignnowStatus())) {
                        addendum.setSignnowStatus(ContractStatus.SIGNED);
                        contractAddendumRepository.save(addendum);
                        log.info("[Webhook] Addendum {} set to SIGNED (document.complete event - all signers signed: {}/{})", 
                                addendum.getId(), signedCount, totalSigners);
                    }
                    boolean withHistory = props.getWebhook().isWithHistory();
                    int version = addendum.getVersion();
                    processSignedPdf(addendum.getId(), documentId, version, false, withHistory);
                } else if (totalSigners > 0 && signedCount < totalSigners) {
                    log.warn("[Webhook] Addendum {} document.complete but API shows {}/{} signed (race condition), still setting SIGNED", 
                            addendum.getId(), signedCount, totalSigners);
                    if (!isCompleted(addendum.getSignnowStatus())) {
                        addendum.setSignnowStatus(ContractStatus.SIGNED);
                        contractAddendumRepository.save(addendum);
                        log.info("[Webhook] Addendum {} set to SIGNED (document.complete event - trusting event despite API delay)", 
                                addendum.getId());
                    }
                    try {
                        boolean withHistory = props.getWebhook().isWithHistory();
                        int version = addendum.getVersion();
                        processSignedPdf(addendum.getId(), documentId, version, false, withHistory);
                    } catch (Exception e) {
                        log.warn("[Webhook] Addendum {} PDF download failed (will retry later): {}", addendum.getId(), e.getMessage());
                    }
                } else {
                    log.warn("[Webhook] Addendum {} document.complete but totalSigners=0, setting SIGNED anyway", 
                            addendum.getId());
                    if (!isCompleted(addendum.getSignnowStatus())) {
                        addendum.setSignnowStatus(ContractStatus.SIGNED);
                        contractAddendumRepository.save(addendum);
                        log.info("[Webhook] Addendum {} set to SIGNED (document.complete event - trusting event)", 
                                addendum.getId());
                    }
                }
                
                return ResponseEntity.ok("ok");
            } catch (AppException ex) {
                log.warn("[Webhook] Addendum {} download/status check failed: {}", 
                        addendum.getId(), ex.getMessage());
                return ResponseEntity.accepted().body("not ready");
            } catch (Exception ex) {
                log.error("[Webhook] Addendum {} processSignedPdf failed", addendum.getId(), ex);
                return ResponseEntity.internalServerError().body("error");
            }
        }

        if (isFieldInviteComplete || isSignedEvent) {
            try {
                updateAddendumSigningStatus(addendum, documentId);
                return ResponseEntity.ok("addendum status updated");
            } catch (Exception ex) {
                log.error("[Webhook] updateAddendumSigningStatus failed for addendum {}", addendum.getId(), ex);
                if (addendum.getSignnowStatus() == ContractStatus.OUT_FOR_SIGNATURE) {
                    addendum.setSignnowStatus(ContractStatus.PARTIALLY_SIGNED);
                    contractAddendumRepository.save(addendum);
                    log.warn("[Webhook] Addendum {} set to PARTIALLY_SIGNED due to exception in updateAddendumSigningStatus", 
                            addendum.getId());
                }
                return ResponseEntity.ok("addendum status update failed but updated");
            }
        }

        return ResponseEntity.ok("ignored");
    }

    private void processSignedPdf(Long entityId, String documentId, Integer version, boolean isContract, boolean withHistory) {
        if (documentId == null || documentId.isBlank()) {
            throw new AppException(ErrorCode.SIGNNOW_DOC_ID_NOT_FOUND);
        }

        byte[] pdf;
        try {
            pdf = signNowClient.downloadFinalPdf(documentId, withHistory);
            log.info("[Webhook] Downloaded signed PDF bytes={} (isContract={}, entityId={})", 
                    (pdf == null ? 0 : pdf.length), isContract, entityId);
        } catch (org.springframework.web.reactive.function.client.WebClientResponseException ex) {
            int sc = ex.getStatusCode().value();
            if (sc == 400 || sc == 409 || sc == 422) {
                log.warn("[Webhook] Collapsed not ready: status={} body={}", sc, ex.getResponseBodyAsString());
                throw new AppException(ErrorCode.SIGNNOW_DOC_NOT_COMPLETED);
            }
            log.error("[Webhook] Download failed: status={} body={}", sc, ex.getResponseBodyAsString());
            throw new AppException(ErrorCode.SIGNNOW_DOWNLOAD_FAILED);
        } catch (IllegalStateException ex) {
            log.error("[Webhook] Download failed: empty body (200 OK)", ex);
            throw new AppException(ErrorCode.SIGNNOW_DOWNLOAD_FAILED);
        } catch (Exception ex) {
            log.error("[Webhook] Download failed (unexpected)", ex);
            throw new AppException(ErrorCode.SIGNNOW_DOWNLOAD_FAILED);
        }

        if (isContract) {
            ContractDocument existing = contractDocumentRepository
                    .findTopByContract_IdAndTypeOrderByVersionDesc(entityId, ContractDocumentType.SIGNED)
                    .orElse(null);
            
            if (existing != null) {
                log.warn("[Webhook] Contract {} already has signed document, skipping save", entityId);
                return;
            }

            String fileName = "signed_v" + version + ".pdf";
            String storageUrl = fileKeyGenerator.generateContractDocumentKey(entityId, fileName);
            fileStorageService.uploadBytes(pdf, storageUrl, "application/pdf");

            ContractDocument doc = new ContractDocument();
            Contract contract = contractRepository.findById(entityId)
                    .orElseThrow(() -> new AppException(ErrorCode.CONTRACT_NOT_FOUND));
            doc.setContract(contract);
            doc.setType(ContractDocumentType.SIGNED);
            doc.setVersion(version);
            doc.setStorageUrl(storageUrl);
            contractDocumentRepository.save(doc);

            log.info("[Webhook] Successfully saved signed contract {} version {}", entityId, version);
        } else {
            AddendumDocument existing = addendumDocumentRepository
                    .findFirstByAddendumIdAndTypeOrderByVersionDesc(entityId, AddendumDocumentType.SIGNED)
                    .orElse(null);
            
            if (existing != null) {
                log.warn("[Webhook] Addendum {} already has signed document, skipping save", entityId);
                return;
            }

            String fileName = "addendum-signed.pdf";
            String storageUrl = fileKeyGenerator.generateContractDocumentKey(entityId, fileName);
            fileStorageService.uploadBytes(pdf, storageUrl, "application/pdf");

            ContractAddendum addendum = contractAddendumRepository.findById(entityId)
                    .orElseThrow(() -> new AppException(ErrorCode.CONTRACT_NOT_FOUND));
            AddendumDocument doc = AddendumDocument.builder()
                    .addendum(addendum)
                    .type(AddendumDocumentType.SIGNED)
                    .version(version)
                    .storageUrl(storageUrl)
                    .signnowDocumentId(documentId)
                    .build();
            addendumDocumentRepository.save(doc);

            log.info("[Webhook] Successfully saved signed addendum {} version {}", entityId, version);
            // Lưu ý: Không cập nhật contract và milestones ở đây vì chỉ khi thanh toán (PAID) mới cập nhật
        }
    }

    private void updateContractSigningStatus(Contract contract, String documentId) {
        try {
            if (isCompleted(contract.getSignnowStatus())) {
                log.debug("[Webhook] Contract {} already signed/completed, skipping status update", contract.getId());
                return;
            }

            Map<String, Integer> signingStatus = signNowClient.getSigningStatus(documentId);
            int totalSigners = signingStatus.getOrDefault("totalSigners", 0);
            int signedCount = signingStatus.getOrDefault("signedCount", 0);
            int isCompleted = signingStatus.getOrDefault("isCompleted", 0);

            log.info("[Webhook] Contract {} signing status: {}/{} signed, completed={}", 
                    contract.getId(), signedCount, totalSigners, isCompleted);

            if (totalSigners == 0) {
                log.warn("[Webhook] Contract {} has no signers found in document", contract.getId());
                if (contract.getSignnowStatus() == ContractStatus.OUT_FOR_SIGNATURE) {
                    contract.setSignnowStatus(ContractStatus.PARTIALLY_SIGNED);
                    contractRepository.save(contract);
                    log.warn("[Webhook] Contract {} set to PARTIALLY_SIGNED (totalSigners=0 but received signed event, API may be delayed)", 
                            contract.getId());
                }
                return;
            }

            if (isCompleted == 1 && signedCount >= totalSigners && totalSigners > 0) {
                contract.setSignnowStatus(ContractStatus.SIGNED);
                contractRepository.save(contract);
                log.info("[Webhook] Contract {} updated to SIGNED (all signers signed: {}/{})", 
                        contract.getId(), signedCount, totalSigners);
            } else if (signedCount > 0 && signedCount < totalSigners) {
                if (contract.getSignnowStatus() != ContractStatus.PARTIALLY_SIGNED) {
                    contract.setSignnowStatus(ContractStatus.PARTIALLY_SIGNED);
                    contractRepository.save(contract);
                    log.info("[Webhook] Contract {} updated to PARTIALLY_SIGNED ({}/{} signed)", 
                            contract.getId(), signedCount, totalSigners);
                } else {
                    log.debug("[Webhook] Contract {} already PARTIALLY_SIGNED ({}/{} signed)", 
                            contract.getId(), signedCount, totalSigners);
                }
            } else if (signedCount == 0) {
                if (contract.getSignnowStatus() == ContractStatus.OUT_FOR_SIGNATURE) {
                    contract.setSignnowStatus(ContractStatus.PARTIALLY_SIGNED);
                    contractRepository.save(contract);
                    log.warn("[Webhook] Contract {} signedCount=0 but received signed event, set to PARTIALLY_SIGNED (API may be delayed)", 
                            contract.getId());
                } else {
                    log.debug("[Webhook] Contract {} signedCount=0, keeping status {}", 
                            contract.getId(), contract.getSignnowStatus());
                }
            } else {
                if (signedCount == totalSigners && totalSigners > 0) {
                    contract.setSignnowStatus(ContractStatus.SIGNED);
                    contractRepository.save(contract);
                    log.info("[Webhook] Contract {} updated to SIGNED ({}/{} signed, isCompleted={} but all signed)", 
                            contract.getId(), signedCount, totalSigners, isCompleted);
                }
            }
        } catch (Exception ex) {
            log.warn("[Webhook] Failed to update signing status for contract {}: {}", contract.getId(), ex.getMessage());
            if (contract.getSignnowStatus() == ContractStatus.OUT_FOR_SIGNATURE) {
                contract.setSignnowStatus(ContractStatus.PARTIALLY_SIGNED);
                contractRepository.save(contract);
                log.warn("[Webhook] Contract {} set to PARTIALLY_SIGNED due to exception in status check", 
                        contract.getId());
            }
            throw ex;
        }
    }

    private void updateAddendumSigningStatus(ContractAddendum addendum, String documentId) {
        try {
            if (isCompleted(addendum.getSignnowStatus())) {
                log.debug("[Webhook] Addendum {} already signed/completed, skipping status update", addendum.getId());
                return;
            }

            Map<String, Integer> signingStatus = signNowClient.getSigningStatus(documentId);
            int totalSigners = signingStatus.getOrDefault("totalSigners", 0);
            int signedCount = signingStatus.getOrDefault("signedCount", 0);
            int isCompleted = signingStatus.getOrDefault("isCompleted", 0);

            log.info("[Webhook] Addendum {} signing status: {}/{} signed, completed={}", 
                    addendum.getId(), signedCount, totalSigners, isCompleted);

            if (totalSigners == 0) {
                log.warn("[Webhook] Addendum {} has no signers found in document", addendum.getId());
                if (addendum.getSignnowStatus() == ContractStatus.OUT_FOR_SIGNATURE) {
                    addendum.setSignnowStatus(ContractStatus.PARTIALLY_SIGNED);
                    contractAddendumRepository.save(addendum);
                    log.warn("[Webhook] Addendum {} set to PARTIALLY_SIGNED (totalSigners=0 but received signed event, API may be delayed)", 
                            addendum.getId());
                }
                return;
            }

            if (isCompleted == 1 && signedCount >= totalSigners && totalSigners > 0) {
                addendum.setSignnowStatus(ContractStatus.SIGNED);
                contractAddendumRepository.save(addendum);
                log.info("[Webhook] Addendum {} updated to SIGNED (all signers signed: {}/{})", 
                        addendum.getId(), signedCount, totalSigners);
            } else if (signedCount > 0 && signedCount < totalSigners) {
                if (addendum.getSignnowStatus() != ContractStatus.PARTIALLY_SIGNED) {
                    addendum.setSignnowStatus(ContractStatus.PARTIALLY_SIGNED);
                    contractAddendumRepository.save(addendum);
                    log.info("[Webhook] Addendum {} updated to PARTIALLY_SIGNED ({}/{} signed)", 
                            addendum.getId(), signedCount, totalSigners);
                } else {
                    log.debug("[Webhook] Addendum {} already PARTIALLY_SIGNED ({}/{} signed)", 
                            addendum.getId(), signedCount, totalSigners);
                }
            } else if (signedCount == 0) {
                if (addendum.getSignnowStatus() == ContractStatus.OUT_FOR_SIGNATURE) {
                    addendum.setSignnowStatus(ContractStatus.PARTIALLY_SIGNED);
                    contractAddendumRepository.save(addendum);
                    log.warn("[Webhook] Addendum {} signedCount=0 but received signed event, set to PARTIALLY_SIGNED (API may be delayed)", 
                            addendum.getId());
                } else {
                    log.debug("[Webhook] Addendum {} signedCount=0, keeping status {}", 
                            addendum.getId(), addendum.getSignnowStatus());
                }
            } else {
                if (signedCount == totalSigners && totalSigners > 0) {
                    addendum.setSignnowStatus(ContractStatus.SIGNED);
                    contractAddendumRepository.save(addendum);
                    log.info("[Webhook] Addendum {} updated to SIGNED ({}/{} signed, isCompleted={} but all signed)", 
                            addendum.getId(), signedCount, totalSigners, isCompleted);
                }
            }
        } catch (Exception ex) {
            log.warn("[Webhook] Failed to update signing status for addendum {}: {}", addendum.getId(), ex.getMessage());
            if (addendum.getSignnowStatus() == ContractStatus.OUT_FOR_SIGNATURE) {
                addendum.setSignnowStatus(ContractStatus.PARTIALLY_SIGNED);
                contractAddendumRepository.save(addendum);
                log.warn("[Webhook] Addendum {} set to PARTIALLY_SIGNED due to exception in status check", 
                        addendum.getId());
            }
            throw ex;
        }
    }

    /**
     * Gửi thông báo và email cho owner khi tất cả đã ký (client đã ký)
     */
    private void sendNotificationToOwnerWhenAllSigned(Contract contract) {
        try {
            Project project = contract.getProject();
            if (project == null) {
                log.warn("[Webhook] Contract {} has no project, cannot send notification to owner", contract.getId());
                return;
            }
            
            User owner = project.getCreator();
            if (owner == null) {
                log.warn("[Webhook] Contract {} has no owner, cannot send notification", contract.getId());
                return;
            }
            
            String ownerEmail = owner.getEmail();
            if (ownerEmail == null || ownerEmail.isBlank()) {
                log.warn("[Webhook] Owner has no email for contract {}", contract.getId());
                return;
            }
            
            String projectTitle = project.getTitle() != null ? project.getTitle() : "Dự án";
            String actionUrl = String.format("/contractSpace?id=%d", project.getId());
            String ownerName = owner.getFullName() != null ? owner.getFullName() : "Owner";
            
            // Gửi thông báo
            notificationService.sendNotification(
                    SendNotificationRequest.builder()
                            .userId(owner.getId())
                            .type(NotificationType.CONTRACT_SIGNING)
                            .title("Hợp đồng đã được ký hoàn tất")
                            .message(String.format("Hợp đồng của dự án \"%s\" đã được ký hoàn tất bởi tất cả các bên.", projectTitle))
                            .relatedEntityType(RelatedEntityType.CONTRACT)
                            .relatedEntityId(contract.getId())
                            .actionUrl(actionUrl)
                            .build()
            );
            
            // Gửi email qua Kafka
            try {
                NotificationEvent event = NotificationEvent.builder()
                        .subject("Hợp đồng đã được ký hoàn tất - " + projectTitle)
                        .recipient(ownerEmail)
                        .templateCode("contract-all-signed")
                        .param(new HashMap<>())
                        .build();
                
                event.getParam().put("recipient", ownerEmail);
                event.getParam().put("recipientName", ownerName);
                event.getParam().put("projectId", String.valueOf(project.getId()));
                event.getParam().put("projectTitle", projectTitle);
                event.getParam().put("contractId", String.valueOf(contract.getId()));
                event.getParam().put("actionUrl", frontendProperties + actionUrl);
                
                kafkaTemplate.send(NOTIFICATION_TOPIC, event);
                log.info("[Webhook] Đã gửi email và thông báo cho owner khi tất cả đã ký - Contract {}", contract.getId());
            } catch (Exception ex) {
                log.error("[Webhook] Lỗi khi gửi email qua Kafka cho owner: {}", ex.getMessage());
            }
        } catch (Exception ex) {
            log.error("[Webhook] Lỗi khi gửi thông báo cho owner khi tất cả đã ký: {}", ex.getMessage());
        }
    }

    private boolean isCompleted(ContractStatus status) {
        return status == ContractStatus.SIGNED || 
               status == ContractStatus.PAID || 
               status == ContractStatus.COMPLETED;
    }
}

