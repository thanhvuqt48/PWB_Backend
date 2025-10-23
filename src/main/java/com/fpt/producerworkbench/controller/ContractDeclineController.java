package com.fpt.producerworkbench.controller;

import com.fpt.producerworkbench.dto.response.ApiResponse;
import com.fpt.producerworkbench.exception.AppException;
import com.fpt.producerworkbench.exception.ErrorCode;
import com.fpt.producerworkbench.repository.ContractRepository;
import com.fpt.producerworkbench.entity.Contract;
import com.fpt.producerworkbench.common.ContractStatus;
import com.fpt.producerworkbench.service.EmailService;
import org.springframework.kafka.core.KafkaTemplate;
import com.fpt.producerworkbench.dto.event.NotificationEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/contracts")
@RequiredArgsConstructor
@Slf4j
public class ContractDeclineController {

    private final ContractRepository contractRepository;
    private final EmailService emailService;
    private final KafkaTemplate<String, NotificationEvent> kafkaTemplate;

    @PostMapping("/{id}/decline")
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

        // Check if contract can be declined
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
                com.fpt.producerworkbench.dto.event.NotificationEvent evt = com.fpt.producerworkbench.dto.event.NotificationEvent.builder()
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

    @GetMapping("/{id}/decline-reason")
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
}


