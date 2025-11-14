package com.fpt.producerworkbench.controller;

import com.fpt.producerworkbench.common.ContractDocumentType;
import com.fpt.producerworkbench.common.ContractStatus;
import com.fpt.producerworkbench.dto.event.NotificationEvent;
import com.fpt.producerworkbench.dto.request.ContractInviteRequest;
import com.fpt.producerworkbench.dto.response.ApiResponse;
import com.fpt.producerworkbench.dto.response.StartSigningResponse;
import com.fpt.producerworkbench.entity.Contract;
import com.fpt.producerworkbench.entity.Project;
import com.fpt.producerworkbench.exception.AppException;
import com.fpt.producerworkbench.exception.ErrorCode;
import com.fpt.producerworkbench.repository.ContractDocumentRepository;
import com.fpt.producerworkbench.repository.ContractRepository;
import com.fpt.producerworkbench.service.ContractInviteService;
import com.fpt.producerworkbench.service.EmailService;
import com.fpt.producerworkbench.service.FileStorageService;
import com.fpt.producerworkbench.service.ProjectPermissionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
public class ProjectContractController {

    private final ContractRepository contractRepository;
    private final ContractDocumentRepository contractDocumentRepository;
    private final FileStorageService fileStorageService;
    private final ContractInviteService contractInviteService;
    private final EmailService emailService;
    private final KafkaTemplate<String, NotificationEvent> kafkaTemplate;
    private final ProjectPermissionService projectPermissionService;

    @GetMapping("/projects/{projectId}/contract")
    public ApiResponse<Map<String, Object>> getContractByProject(@PathVariable Long projectId) {
        Contract c = contractRepository.findByProjectId(projectId)
                .orElseThrow(() -> new AppException(ErrorCode.CONTRACT_NOT_FOUND));

        var resp = new HashMap<String, Object>();
        resp.put("id", c.getId());
        resp.put("status", c.getStatus());
        resp.put("signnowStatus", c.getSignnowStatus());

        var signed = contractDocumentRepository
                .findTopByContract_IdAndTypeOrderByVersionDesc(c.getId(), ContractDocumentType.SIGNED)
                .orElse(null);
        if (signed != null) {
            resp.put("documentVersion", signed.getVersion());
            resp.put("documentType", "SIGNED");
            resp.put("documentUrl", fileStorageService.generatePresignedUrl(signed.getStorageUrl(), false, null));
        } else {
            var filled = contractDocumentRepository
                    .findTopByContract_IdAndTypeOrderByVersionDesc(c.getId(), ContractDocumentType.FILLED)
                    .orElse(null);
            if (filled != null) {
                resp.put("documentVersion", filled.getVersion());
                resp.put("documentType", "FILLED");
                resp.put("documentUrl", fileStorageService.generatePresignedUrl(filled.getStorageUrl(), false, null));
            }
        }

        return ApiResponse.<Map<String, Object>>builder().code(200).result(resp).build();
    }

    @PostMapping("/contracts/{id}/invites")
    public ApiResponse<StartSigningResponse> invite(@PathVariable Long id,
                                                    @RequestBody(required = false) ContractInviteRequest req,
                                                    Authentication auth) {
        if (req == null) {
            req = new ContractInviteRequest();
        }
        var result = contractInviteService.invite(auth, id, req);
        return ApiResponse.<StartSigningResponse>builder().code(200).result(result).build();
    }

    @PostMapping("/contracts/{id}/decline")
    public ApiResponse<String> decline(@PathVariable Long id,
                                       @RequestBody String reason,
                                       Authentication auth) throws Exception {
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

        if (c.getStatus() == ContractStatus.COMPLETED) {
            throw new AppException(ErrorCode.CONTRACT_ALREADY_COMPLETED);
        }
        if (c.getStatus() == ContractStatus.DECLINED) {
            throw new AppException(ErrorCode.CONTRACT_ALREADY_DECLINED);
        }

        c.setStatus(ContractStatus.DECLINED);
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
                        .param(new java.util.HashMap<>())
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

        return ApiResponse.<String>builder().code(200).result("DECLINED").build();
    }

    @GetMapping("/contracts/{id}/decline-reason")
    public ApiResponse<String> getDeclineReason(@PathVariable Long id, Authentication auth) {
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

        if (c.getStatus() != ContractStatus.DECLINED) {
            throw new AppException(ErrorCode.CONTRACT_NOT_DECLINED);
        }

        return ApiResponse.<String>builder()
                .code(200)
                .result(c.getDeclineReason() != null ? c.getDeclineReason() : "Không có lý do cụ thể")
                .build();
    }

    private void ensureCanViewFilled(Authentication auth, Long contractId) {
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

    @GetMapping("/contracts/{id}/filled/file")
    public ResponseEntity<Void> redirectToFilled(@PathVariable("id") Long id, Authentication auth) {
        ensureCanViewFilled(auth, id);

        var doc = contractDocumentRepository
                .findTopByContract_IdAndTypeOrderByVersionDesc(id, ContractDocumentType.FILLED)
                .orElseThrow(() -> new AppException(ErrorCode.CONTRACT_DOC_NOT_FOUND));

        String url = fileStorageService.generatePresignedUrl(doc.getStorageUrl(), false, null);
        return ResponseEntity.status(302).header("Location", url).build();
    }

    // Endpoint từ ContractSigningController
    @GetMapping("/contracts/{id}/signed/file")
    public ResponseEntity<Void> viewSignedPdf(@PathVariable Long id) {
        var doc = contractDocumentRepository
                .findTopByContract_IdAndTypeOrderByVersionDesc(id, ContractDocumentType.SIGNED)
                .orElseThrow(() -> new AppException(ErrorCode.CONTRACT_DOC_NOT_FOUND));

        String url = fileStorageService.generatePresignedUrl(doc.getStorageUrl(), false, null);
        return ResponseEntity.status(302).header(HttpHeaders.LOCATION, url).build();
    }
}


