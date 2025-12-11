package com.fpt.producerworkbench.service.impl;

import com.fpt.producerworkbench.common.AddendumDocumentType;
import com.fpt.producerworkbench.common.ContractStatus;
import com.fpt.producerworkbench.common.NotificationType;
import com.fpt.producerworkbench.common.RelatedEntityType;
import com.fpt.producerworkbench.dto.event.NotificationEvent;
import com.fpt.producerworkbench.dto.request.SendNotificationRequest;
import com.fpt.producerworkbench.entity.Contract;
import com.fpt.producerworkbench.entity.ContractAddendum;
import com.fpt.producerworkbench.entity.Project;
import com.fpt.producerworkbench.entity.User;
import com.fpt.producerworkbench.exception.AppException;
import com.fpt.producerworkbench.exception.ErrorCode;
import com.fpt.producerworkbench.repository.AddendumDocumentRepository;
import com.fpt.producerworkbench.repository.ContractAddendumRepository;
import com.fpt.producerworkbench.repository.ContractRepository;
import com.fpt.producerworkbench.service.ContractAddendumService;
import com.fpt.producerworkbench.service.EmailService;
import com.fpt.producerworkbench.service.FileStorageService;
import com.fpt.producerworkbench.service.NotificationService;
import com.fpt.producerworkbench.service.ProjectPermissionService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class ContractAddendumServiceImpl implements ContractAddendumService {

    ContractRepository contractRepository;
    ContractAddendumRepository addendumRepository;
    AddendumDocumentRepository addendumDocumentRepository;
    FileStorageService fileStorageService;
    ProjectPermissionService projectPermissionService;
    EmailService emailService;
    KafkaTemplate<String, NotificationEvent> kafkaTemplate;
    NotificationService notificationService;

    @Override
    public List<Map<String, Object>> getAllAddendumsByContract(Long contractId) {
        contractRepository.findById(contractId)
                .orElseThrow(() -> new AppException(ErrorCode.CONTRACT_NOT_FOUND));

        int maxAddendumNumber = addendumRepository.findMaxAddendumNumber(contractId);
        
        List<Map<String, Object>> result = new java.util.ArrayList<>();
        
        for (int addendumNum = 1; addendumNum <= maxAddendumNumber; addendumNum++) {
            ContractAddendum addendum = addendumRepository
                    .findFirstByContractIdAndAddendumNumberOrderByVersionDesc(contractId, addendumNum)
                    .orElse(null);
            
            if (addendum == null) continue;
            Map<String, Object> addendumInfo = new HashMap<>();
            addendumInfo.put("id", addendum.getId());
            addendumInfo.put("addendumNumber", addendum.getAddendumNumber());
            addendumInfo.put("version", addendum.getVersion());
            addendumInfo.put("title", addendum.getTitle());
            addendumInfo.put("effectiveDate", addendum.getEffectiveDate());
            addendumInfo.put("signnowStatus", addendum.getSignnowStatus());
            
            boolean isPaid = addendum.getSignnowStatus() == ContractStatus.PAID || addendum.getSignnowStatus() == ContractStatus.COMPLETED;
            addendumInfo.put("isPaid", isPaid);

            var signed = addendumDocumentRepository
                    .findFirstByAddendumIdAndTypeOrderByVersionDesc(addendum.getId(), AddendumDocumentType.SIGNED)
                    .orElse(null);
            if (signed != null) {
                addendumInfo.put("documentType", "SIGNED");
                addendumInfo.put("documentUrl", fileStorageService.generatePresignedUrl(signed.getStorageUrl(), false, null));
            } else {
                var filledDoc = addendumDocumentRepository
                        .findFirstByAddendumIdAndTypeOrderByVersionDesc(addendum.getId(), AddendumDocumentType.FILLED)
                        .orElse(null);
                if (filledDoc != null) {
                    addendumInfo.put("documentType", "FILLED");
                    addendumInfo.put("documentUrl", fileStorageService.generatePresignedUrl(filledDoc.getStorageUrl(), false, null));
                }
            }

            result.add(addendumInfo);
        }

        return result;
    }

    @Override
    public Map<String, Object> getAddendumByContract(Long contractId) {
        ContractAddendum addendum = addendumRepository
                .findFirstByContractIdOrderByAddendumNumberDescVersionDesc(contractId)
                .orElse(null);

        var resp = new HashMap<String, Object>();
        
        if (addendum == null) {
            resp.put("exists", false);
            return resp;
        }

        resp.put("exists", true);
        resp.put("id", addendum.getId());
        resp.put("addendumNumber", addendum.getAddendumNumber());
        resp.put("title", addendum.getTitle());
        resp.put("version", addendum.getVersion());
        resp.put("effectiveDate", addendum.getEffectiveDate());
        resp.put("signnowStatus", addendum.getSignnowStatus());
        resp.put("numOfMoney", addendum.getNumOfMoney());
        resp.put("numOfEdit", addendum.getNumOfEdit());
        resp.put("numOfRefresh", addendum.getNumOfRefresh());
        resp.put("pitTax", addendum.getPitTax());
        resp.put("vatTax", addendum.getVatTax());
        
        boolean isPaid = addendum.getSignnowStatus() == ContractStatus.PAID || addendum.getSignnowStatus() == ContractStatus.COMPLETED;
        resp.put("isPaid", isPaid);

        var signed = addendumDocumentRepository
                .findFirstByAddendumIdAndTypeOrderByVersionDesc(addendum.getId(), AddendumDocumentType.SIGNED)
                .orElse(null);
        if (signed != null) {
            resp.put("documentVersion", signed.getVersion());
            resp.put("documentType", "SIGNED");
            resp.put("documentUrl", fileStorageService.generatePresignedUrl(signed.getStorageUrl(), false, null));
        } else {
            var filledDoc = addendumDocumentRepository
                    .findFirstByAddendumIdAndTypeOrderByVersionDesc(addendum.getId(), AddendumDocumentType.FILLED)
                    .orElse(null);
            if (filledDoc != null) {
                resp.put("documentVersion", filledDoc.getVersion());
                resp.put("documentType", "FILLED");
                resp.put("documentUrl", fileStorageService.generatePresignedUrl(filledDoc.getStorageUrl(), false, null));
            }
        }

        return resp;
    }

    @Override
    public String getAddendumFileUrl(Long contractId, Authentication auth) {
        ensureCanViewAddendum(auth, contractId);

        ContractAddendum addendum = addendumRepository
                .findFirstByContractIdOrderByAddendumNumberDescVersionDesc(contractId)
                .orElseThrow(() -> new AppException(ErrorCode.BAD_REQUEST));

        var doc = addendumDocumentRepository
                .findFirstByAddendumIdAndTypeOrderByVersionDesc(addendum.getId(), AddendumDocumentType.FILLED)
                .orElseThrow(() -> new AppException(ErrorCode.CONTRACT_DOC_NOT_FOUND));

        return fileStorageService.generatePresignedUrl(doc.getStorageUrl(), false, null);
    }

    @Override
    public String getSignedAddendumFileUrl(Long contractId) {
        ContractAddendum addendum = addendumRepository
                .findFirstByContractIdOrderByAddendumNumberDescVersionDesc(contractId)
                .orElseThrow(() -> new AppException(ErrorCode.BAD_REQUEST));

        var doc = addendumDocumentRepository
                .findFirstByAddendumIdAndTypeOrderByVersionDesc(addendum.getId(), AddendumDocumentType.SIGNED)
                .orElseThrow(() -> new AppException(ErrorCode.CONTRACT_DOC_NOT_FOUND));

        return fileStorageService.generatePresignedUrl(doc.getStorageUrl(), false, null);
    }

    @Override
    public String declineAddendum(Long contractId, String reason, Authentication auth) throws Exception {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new AppException(ErrorCode.CONTRACT_NOT_FOUND));

        ContractAddendum addendum = addendumRepository
                .findFirstByContractIdOrderByAddendumNumberDescVersionDesc(contractId)
                .orElseThrow(() -> new AppException(ErrorCode.BAD_REQUEST));

        String email = auth == null ? null : auth.getName();
        if (email == null || email.isBlank()) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        boolean isAdmin = auth.getAuthorities().stream().anyMatch(a -> "ADMIN".equalsIgnoreCase(a.getAuthority()));
        boolean isClient = contract.getProject() != null && contract.getProject().getClient() != null
                && email.equalsIgnoreCase(contract.getProject().getClient().getEmail());
        if (!isAdmin && !isClient) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }

        if (addendum.getSignnowStatus() == ContractStatus.SIGNED || 
            addendum.getSignnowStatus() == ContractStatus.PAID || 
            addendum.getSignnowStatus() == ContractStatus.COMPLETED) {
            throw new AppException(ErrorCode.CONTRACT_ALREADY_COMPLETED);
        }
        if (addendum.getSignnowStatus() == ContractStatus.DECLINED) {
            throw new AppException(ErrorCode.CONTRACT_ALREADY_DECLINED);
        }

        addendum.setSignnowStatus(ContractStatus.DECLINED);
        addendum.setDeclineReason(reason);
        addendumRepository.save(addendum);
        
        log.info("Addendum {} (contract {}) đã được từ chối với lý do: {}", addendum.getId(), contractId, reason);

        String ownerEmail = contract.getProject() != null && contract.getProject().getCreator() != null
                ? contract.getProject().getCreator().getEmail()
                : null;
        
        log.info("Owner email để gửi thông báo: {}", ownerEmail);
        if (ownerEmail != null) {
            try {
                NotificationEvent evt = NotificationEvent.builder()
                        .subject("Phụ lục hợp đồng bị từ chối - Contract #" + contractId)
                        .recipient(ownerEmail)
                        .templateCode("contract-addendum-declined")
                        .param(new HashMap<>())
                        .build();
                evt.getParam().put("recipient", ownerEmail);
                evt.getParam().put("projectId", String.valueOf(contract.getProject().getId()));
                evt.getParam().put("projectTitle", contract.getProject().getTitle());
                evt.getParam().put("contractId", String.valueOf(contractId));
                evt.getParam().put("addendumId", String.valueOf(addendum.getId()));
                evt.getParam().put("addendumTitle", addendum.getTitle() != null ? addendum.getTitle() : "Phụ lục hợp đồng");
                evt.getParam().put("reason", reason == null ? "(không cung cấp)" : reason);

                kafkaTemplate.send("notification-delivery", evt);
                log.info("Đã gửi notification event qua Kafka cho owner: {}", ownerEmail);
            } catch (Exception ex) {
                log.error("Lỗi khi gửi email qua Kafka, thử gửi trực tiếp: {}", ex.getMessage());
                try {
                    String subject = "Phụ lục hợp đồng bị từ chối - Contract #" + contractId;
                    String content = "<p>Phụ lục hợp đồng đã bị từ chối với lý do:</p><p>" + (reason == null ? "(không cung cấp)" : reason) + "</p>"
                            + "<p>Vui lòng chỉnh sửa và gửi lại để khách hàng duyệt tiếp.</p>";
                    emailService.sendEmail(subject, content, List.of(ownerEmail));
                    log.info("Đã gửi email trực tiếp cho owner: {}", ownerEmail);
                } catch (Exception emailEx) {
                    log.error("Lỗi khi gửi email trực tiếp: {}", emailEx.getMessage());
                }
            }
        } else {
            log.warn("Không tìm thấy email của owner để gửi thông báo từ chối phụ lục hợp đồng");
        }

        try {
            User owner = contract.getProject() != null && contract.getProject().getCreator() != null
                    ? contract.getProject().getCreator()
                    : null;
            
            if (owner != null) {
                String actionUrl = String.format("/contractId=%d", contract.getProject().getId());
                String addendumTitle = addendum.getTitle() != null ? addendum.getTitle() : "Phụ lục hợp đồng";
                String declineReason = reason != null ? reason : "(không cung cấp)";
                
                notificationService.sendNotification(
                        SendNotificationRequest.builder()
                                .userId(owner.getId())
                                .type(NotificationType.CONTRACT_SIGNING)
                                .title("Phụ lục hợp đồng bị từ chối")
                                .message(String.format("Phụ lục hợp đồng \"%s\" của dự án \"%s\" đã bị từ chối.%s",
                                        addendumTitle,
                                        contract.getProject().getTitle(),
                                        " Lý do: " + declineReason))
                                .relatedEntityType(RelatedEntityType.CONTRACT)
                                .relatedEntityId(contractId)
                                .actionUrl(actionUrl)
                                .build());
            }
        } catch (Exception e) {
            log.error("Gặp lỗi khi gửi notification realtime cho owner khi decline addendum: {}", e.getMessage());
        }

        return "DECLINED";
    }

    @Override
    public String getDeclineReason(Long contractId, Authentication auth) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new AppException(ErrorCode.CONTRACT_NOT_FOUND));

        ContractAddendum addendum = addendumRepository
                .findFirstByContractIdOrderByAddendumNumberDescVersionDesc(contractId)
                .orElseThrow(() -> new AppException(ErrorCode.BAD_REQUEST));

        String email = auth == null ? null : auth.getName();
        if (email == null || email.isBlank()) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        
        boolean isAdmin = auth.getAuthorities().stream().anyMatch(a -> "ADMIN".equalsIgnoreCase(a.getAuthority()));
        boolean isOwner = contract.getProject() != null && contract.getProject().getCreator() != null
                && email.equalsIgnoreCase(contract.getProject().getCreator().getEmail());
        boolean isClient = contract.getProject() != null && contract.getProject().getClient() != null
                && email.equalsIgnoreCase(contract.getProject().getClient().getEmail());
        
        if (!isAdmin && !isOwner && !isClient) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }

        if (addendum.getSignnowStatus() != ContractStatus.DECLINED) {
            throw new AppException(ErrorCode.CONTRACT_NOT_DECLINED);
        }

        return addendum.getDeclineReason() != null ? addendum.getDeclineReason() : "Không có lý do cụ thể";
    }

    @Override
    public void ensureCanViewAddendum(Authentication auth, Long contractId) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new AppException(ErrorCode.CONTRACT_NOT_FOUND));
        Project project = contract.getProject();
        if (project == null) {
            throw new AppException(ErrorCode.PROJECT_NOT_FOUND);
        }

        var permissions = projectPermissionService.checkContractPermissions(auth, project.getId());
        if (!permissions.isCanViewContract()) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }
    }
}

