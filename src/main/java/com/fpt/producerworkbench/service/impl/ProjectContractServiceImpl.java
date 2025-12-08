package com.fpt.producerworkbench.service.impl;

import com.fpt.producerworkbench.common.ContractDocumentType;
import com.fpt.producerworkbench.common.ContractStatus;
import com.fpt.producerworkbench.configuration.SignNowClient;
import com.fpt.producerworkbench.configuration.SignNowProperties;
import com.fpt.producerworkbench.dto.event.NotificationEvent;
import com.fpt.producerworkbench.dto.request.ContractInviteRequest;
import com.fpt.producerworkbench.dto.request.ContractPdfFillRequest;
import com.fpt.producerworkbench.dto.response.PartyBInfoResponse;
import com.fpt.producerworkbench.dto.response.StartSigningResponse;
import com.fpt.producerworkbench.entity.Contract;
import com.fpt.producerworkbench.entity.ContractDocument;
import com.fpt.producerworkbench.entity.Project;
import com.fpt.producerworkbench.entity.User;
import com.fpt.producerworkbench.exception.AppException;
import com.fpt.producerworkbench.exception.ErrorCode;
import com.fpt.producerworkbench.repository.ContractDocumentRepository;
import com.fpt.producerworkbench.repository.ContractRepository;
import com.fpt.producerworkbench.repository.UserRepository;
import com.fpt.producerworkbench.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProjectContractServiceImpl implements ProjectContractService {

    private final ContractRepository contractRepository;
    private final ContractDocumentRepository contractDocumentRepository;
    private final FileStorageService fileStorageService;
    private final ProjectPermissionService projectPermissionService;
    private final ContractInviteService contractInviteService;
    private final ContractPdfService contractPdfService;
    private final EmailService emailService;
    private final KafkaTemplate<String, NotificationEvent> kafkaTemplate;
    private final SignNowClient signNowClient;
    private final SignNowProperties signNowProperties;
    private final FileKeyGenerator fileKeyGenerator;
    private final UserRepository userRepository;

    @Override
    public Map<String, Object> getContractByProject(Long projectId) {
        Contract c = contractRepository.findByProjectId(projectId)
                .orElseThrow(() -> new AppException(ErrorCode.CONTRACT_NOT_FOUND));

        Map<String, Object> resp = new HashMap<>();
        resp.put("id", c.getId());
        resp.put("status", c.getSignnowStatus());
        resp.put("signnowStatus", c.getSignnowStatus());
        
        // isFunded được tính từ contract status: PAID hoặc COMPLETED
        boolean isFunded = c.getSignnowStatus() == ContractStatus.PAID || c.getSignnowStatus() == ContractStatus.COMPLETED;
        resp.put("isFunded", isFunded);

        ContractDocument signed = contractDocumentRepository
                .findTopByContract_IdAndTypeOrderByVersionDesc(c.getId(), ContractDocumentType.SIGNED)
                .orElse(null);
        if (signed != null) {
            resp.put("documentVersion", signed.getVersion());
            resp.put("documentType", "SIGNED");
            resp.put("documentUrl", fileStorageService.generatePresignedUrl(signed.getStorageUrl(), false, null));
        } else {
            ContractDocument filled = contractDocumentRepository
                    .findTopByContract_IdAndTypeOrderByVersionDesc(c.getId(), ContractDocumentType.FILLED)
                    .orElse(null);
            if (filled != null) {
                resp.put("documentVersion", filled.getVersion());
                resp.put("documentType", "FILLED");
                resp.put("documentUrl", fileStorageService.generatePresignedUrl(filled.getStorageUrl(), false, null));
            }
        }

        return resp;
    }

    @Override
    public ResponseEntity<Void> redirectToFilled(Long id, Authentication auth) {
        Contract contract = contractRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.CONTRACT_NOT_FOUND));
        Project project = contract.getProject();
        if (project == null) {
            throw new AppException(ErrorCode.PROJECT_NOT_FOUND);
        }

        var permissions = projectPermissionService.checkContractPermissions(auth, project.getId());
        if (!permissions.isCanViewContract()) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }

        var doc = contractDocumentRepository
                .findTopByContract_IdAndTypeOrderByVersionDesc(id, ContractDocumentType.FILLED)
                .orElseThrow(() -> new AppException(ErrorCode.CONTRACT_DOC_NOT_FOUND));

        String url = fileStorageService.generatePresignedUrl(doc.getStorageUrl(), false, null);
        return ResponseEntity.status(302).header(HttpHeaders.LOCATION, url).build();
    }

    @Override
    public StartSigningResponse invite(Authentication auth, Long contractId, ContractInviteRequest req) {
        return contractInviteService.invite(auth, contractId, req);
    }

    @Override
    public byte[] fillContractPdf(Authentication auth, Long projectId, ContractPdfFillRequest req) {
        return contractPdfService.fillTemplate(auth, projectId, req);
    }

    @Override
    public String decline(Authentication auth, Long id, String reason) throws Exception {
        Contract c = contractRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.CONTRACT_NOT_FOUND));

        String email = auth == null ? null : auth.getName();
        if (email == null || email.isBlank()) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        boolean isAdmin = auth.getAuthorities().stream().anyMatch(a -> "ADMIN".equalsIgnoreCase(a.getAuthority()));
        boolean isClient = c.getProject() != null && c.getProject().getClient() != null
                && email.equalsIgnoreCase(c.getProject().getClient().getEmail());
        if (!isAdmin && !isClient) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }

        // Không thể từ chối nếu đã ký (SIGNED), đã thanh toán (PAID), hoặc hoàn thành (COMPLETED)
        if (c.getSignnowStatus() == ContractStatus.SIGNED || 
            c.getSignnowStatus() == ContractStatus.PAID || 
            c.getSignnowStatus() == ContractStatus.COMPLETED) {
            throw new AppException(ErrorCode.CONTRACT_ALREADY_COMPLETED);
        }
        if (c.getSignnowStatus() == ContractStatus.DECLINED) {
            throw new AppException(ErrorCode.CONTRACT_ALREADY_DECLINED);
        }

        c.setSignnowStatus(ContractStatus.DECLINED);
        c.setDeclineReason(reason);
        contractRepository.save(c);
        
        log.info("Contract {} đã được từ chối với lý do: {}", c.getId(), reason);

        String ownerEmail = c.getProject() != null && c.getProject().getCreator() != null
                ? c.getProject().getCreator().getEmail()
                : null;
        
        log.info("Owner email để gửi thông báo: {}", ownerEmail);
        if (ownerEmail != null) {
            try {
                NotificationEvent evt = NotificationEvent.builder()
                        .subject("Hợp đồng bị từ chối - Contract #" + c.getId())
                        .recipient(ownerEmail)
                        .templateCode("contract-declined")
                        .param(new HashMap<>())
                        .build();
                evt.getParam().put("recipient", ownerEmail);
                evt.getParam().put("projectId", String.valueOf(c.getProject().getId()));
                evt.getParam().put("projectTitle", c.getProject().getTitle());
                evt.getParam().put("contractId", String.valueOf(c.getId()));
                evt.getParam().put("reason", reason == null ? "(không cung cấp)" : reason);

                kafkaTemplate.send("notification-delivery", evt);
                log.info("Đã gửi notification event qua Kafka cho owner: {}", ownerEmail);
            } catch (Exception ex) {
                log.error("Lỗi khi gửi email qua Kafka, thử gửi trực tiếp: {}", ex.getMessage());
                try {
                    String subject = "Hợp đồng bị từ chối - Contract #" + c.getId();
                    String content = "<p>Hợp đồng đã bị từ chối với lý do:</p><p>" + (reason == null ? "(không cung cấp)" : reason) + "</p>"
                            + "<p>Vui lòng chỉnh sửa và gửi lại để khách hàng duyệt tiếp.</p>";
                    emailService.sendEmail(subject, content, List.of(ownerEmail));
                    log.info("Đã gửi email trực tiếp cho owner: {}", ownerEmail);
                } catch (Exception emailEx) {
                    log.error("Lỗi khi gửi email trực tiếp: {}", emailEx.getMessage());
                }
            }
        } else {
            log.warn("Không tìm thấy email của owner để gửi thông báo từ chối hợp đồng");
        }

        return "DECLINED";
    }

    @Override
    public String getDeclineReason(Authentication auth, Long id) {
        Contract c = contractRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.CONTRACT_NOT_FOUND));

        String email = auth == null ? null : auth.getName();
        if (email == null || email.isBlank()) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        
        boolean isAdmin = auth.getAuthorities().stream().anyMatch(a -> "ADMIN".equalsIgnoreCase(a.getAuthority()));
        boolean isOwner = c.getProject() != null && c.getProject().getCreator() != null
                && email.equalsIgnoreCase(c.getProject().getCreator().getEmail());
        boolean isClient = c.getProject() != null && c.getProject().getClient() != null
                && email.equalsIgnoreCase(c.getProject().getClient().getEmail());
        
        if (!isAdmin && !isOwner && !isClient) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }

        if (c.getSignnowStatus() != ContractStatus.DECLINED) {
            throw new AppException(ErrorCode.CONTRACT_NOT_DECLINED);
        }

        return c.getDeclineReason() != null ? c.getDeclineReason() : "Không có lý do cụ thể";
    }

    @Override
    public ResponseEntity<Void> viewSignedPdf(Long id) {
        var doc = contractDocumentRepository
                .findTopByContract_IdAndTypeOrderByVersionDesc(id, ContractDocumentType.SIGNED)
                .orElseThrow(() -> new AppException(ErrorCode.CONTRACT_DOC_NOT_FOUND));

        String url = fileStorageService.generatePresignedUrl(doc.getStorageUrl(), false, null);
        return ResponseEntity.status(302).header(HttpHeaders.LOCATION, url).build();
    }

    @Override
    public Map<String, Object> syncContractStatus(Long projectId) {
        Contract c = contractRepository.findByProjectId(projectId)
                .orElseThrow(() -> new AppException(ErrorCode.CONTRACT_NOT_FOUND));

        // Nếu đã ký (SIGNED, PAID, COMPLETED) thì không cần sync
        if (c.getSignnowStatus() == ContractStatus.SIGNED || 
            c.getSignnowStatus() == ContractStatus.PAID || 
            c.getSignnowStatus() == ContractStatus.COMPLETED) {
            Map<String, Object> resp = new HashMap<>();
            resp.put("message", "Contract already signed/completed");
            resp.put("status", c.getSignnowStatus());
            resp.put("signnowStatus", c.getSignnowStatus());
            return resp;
        }

        if (c.getSignnowDocumentId() == null || c.getSignnowDocumentId().isBlank()) {
            throw new AppException(ErrorCode.SIGNNOW_DOC_ID_NOT_FOUND);
        }

        boolean withHistory = signNowProperties.getWebhook().isWithHistory();
        
        // Check xem document có thể download collapsed không (tức là đã completed)
        boolean canDownload;
        try {
            canDownload = signNowClient.canDownloadCollapsed(c.getSignnowDocumentId(), withHistory);
        } catch (WebClientResponseException ex) {
            int sc = ex.getStatusCode().value();
            log.error("[SyncStatus] Cannot check document status: status={} body={}", sc, ex.getResponseBodyAsString());
            if (sc == 404) {
                throw new AppException(ErrorCode.SIGNNOW_DOC_ID_NOT_FOUND);
            }
            throw new AppException(ErrorCode.SIGNNOW_DOWNLOAD_FAILED);
        } catch (Exception e) {
            log.error("[SyncStatus] Cannot check document status (unexpected): {}", e.getMessage(), e);
            throw new AppException(ErrorCode.SIGNNOW_DOWNLOAD_FAILED);
        }

        if (!canDownload) {
            Map<String, Object> resp = new HashMap<>();
            resp.put("message", "Document is not completed yet on SignNow");
            resp.put("status", c.getSignnowStatus());
            resp.put("signnowStatus", c.getSignnowStatus());
            return resp;
        }

        // Document đã completed, download và update status
        try {
            byte[] pdf = signNowClient.downloadFinalPdf(c.getSignnowDocumentId(), withHistory);
            log.info("[SyncStatus] Downloaded signed PDF bytes={} for contract {}", 
                    (pdf == null ? 0 : pdf.length), c.getId());

            if (pdf == null || pdf.length == 0) {
                log.error("[SyncStatus] Downloaded PDF is null or empty for contract {}", c.getId());
                throw new AppException(ErrorCode.SIGNNOW_DOWNLOAD_FAILED);
            }

            // Check xem đã có signed document chưa
            ContractDocument latest = contractDocumentRepository
                    .findTopByContract_IdAndTypeOrderByVersionDesc(c.getId(), ContractDocumentType.SIGNED)
                    .orElse(null);
            
            if (latest != null) {
                log.warn("[SyncStatus] Contract {} already has signed document, updating status only", c.getId());
            } else {
                // Tạo signed document mới
                int nextVer = 1;
                String fileName = "signed_v" + nextVer + ".pdf";
                String storageUrl = fileKeyGenerator.generateContractDocumentKey(c.getId(), fileName);
                fileStorageService.uploadBytes(pdf, storageUrl, "application/pdf");

                ContractDocument doc = new ContractDocument();
                doc.setContract(c);
                doc.setType(ContractDocumentType.SIGNED);
                doc.setVersion(nextVer);
                doc.setStorageUrl(storageUrl);
                contractDocumentRepository.save(doc);
                log.info("[SyncStatus] Created signed document for contract {}", c.getId());
            }

            // Update status
            c.setSignnowStatus(ContractStatus.COMPLETED);
            contractRepository.save(c);

            Map<String, Object> resp = new HashMap<>();
            resp.put("message", "Contract status synced successfully");
            resp.put("status", c.getSignnowStatus());
            resp.put("signnowStatus", c.getSignnowStatus());
            return resp;

        } catch (AppException ex) {
            // Re-throw AppException để giữ nguyên mã lỗi cụ thể
            throw ex;
        } catch (org.springframework.web.reactive.function.client.WebClientResponseException ex) {
            int sc = ex.getStatusCode().value();
            log.error("[SyncStatus] Download failed: status={} body={}", sc, ex.getResponseBodyAsString());
            if (sc == 400 || sc == 409 || sc == 422) {
                throw new AppException(ErrorCode.SIGNNOW_DOC_NOT_COMPLETED);
            } else if (sc == 404) {
                throw new AppException(ErrorCode.SIGNNOW_DOC_ID_NOT_FOUND);
            } else {
                throw new AppException(ErrorCode.SIGNNOW_DOWNLOAD_FAILED);
            }
        } catch (Exception ex) {
            log.error("[SyncStatus] Sync failed for contract {}: {}", c.getId(), ex.getMessage(), ex);
            throw new AppException(ErrorCode.SIGNNOW_DOWNLOAD_FAILED);
        }
    }

    @Override
    public PartyBInfoResponse getVerifiedPartyBInfo(Authentication auth) {
        // Lấy email từ Authentication
        String email = auth == null ? null : auth.getName();
        if (email == null || email.isBlank()) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        // Lấy user từ database
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        // Kiểm tra user đã xác thực CCCD chưa
        if (user.getIsVerified() == null || !user.getIsVerified()) {
            throw new AppException(ErrorCode.USER_NOT_VERIFIED);
        }

        // Parse date từ String sang LocalDate
        java.time.LocalDate issueDate = parseCccdIssueDate(user.getCccdIssueDate());

        // Map từ User sang PartyBInfoResponse
        return PartyBInfoResponse.builder()
                .bName(user.getCccdFullName())
                .bCccd(user.getCccdNumber())
                .bCccdIssueDate(issueDate)
                .bCccdIssuePlace(user.getCccdIssuePlace())
                .bAddress(user.getCccdRecentLocation())
                .bPhone(user.getPhoneNumber())
                .isVerified(true)
                .build();
    }

    /**
     * Parse CCCD issue date từ String sang LocalDate.
     * Hỗ trợ các format: "dd/MM/yyyy", "yyyy-MM-dd", "yyyy/MM/dd"
     */
    private java.time.LocalDate parseCccdIssueDate(String dateString) {
        if (dateString == null || dateString.isBlank()) {
            log.warn("CCCD issue date is null or blank");
            return null;
        }

        String trimmed = dateString.trim();
        java.time.format.DateTimeFormatter[] formatters = {
                java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"),
                java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"),
                java.time.format.DateTimeFormatter.ofPattern("yyyy/MM/dd")
        };

        for (java.time.format.DateTimeFormatter formatter : formatters) {
            try {
                return java.time.LocalDate.parse(trimmed, formatter);
            } catch (java.time.format.DateTimeParseException e) {
                // Try next format
            }
        }

        log.warn("Cannot parse CCCD issue date: {}", dateString);
        return null;
    }
}

