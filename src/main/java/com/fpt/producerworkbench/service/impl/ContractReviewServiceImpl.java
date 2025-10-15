package com.fpt.producerworkbench.service.impl;

import com.fpt.producerworkbench.common.ContractDocumentType;
import com.fpt.producerworkbench.common.ContractStatus;
import com.fpt.producerworkbench.dto.event.NotificationEvent;
import com.fpt.producerworkbench.dto.request.ContractChangeRequest;
import com.fpt.producerworkbench.entity.Contract;
import com.fpt.producerworkbench.entity.ContractComment;
import com.fpt.producerworkbench.entity.ContractDocument;
import com.fpt.producerworkbench.entity.User;
import com.fpt.producerworkbench.exception.AppException;
import com.fpt.producerworkbench.exception.ErrorCode;
import com.fpt.producerworkbench.repository.ContractCommentRepository;
import com.fpt.producerworkbench.repository.ContractDocumentRepository;
import com.fpt.producerworkbench.repository.ContractRepository;
import com.fpt.producerworkbench.service.ContractReviewService;
import com.fpt.producerworkbench.service.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContractReviewServiceImpl implements ContractReviewService {

    private final ContractRepository contractRepository;
    private final ContractDocumentRepository contractDocumentRepository;
    private final ContractCommentRepository contractCommentRepository;
    private final StorageService storageService;
    private final KafkaTemplate<String, NotificationEvent> kafkaTemplate;

    /**
     * Phương thức helper để xác thực token và trạng thái hợp đồng.
     * Dùng chung cho các hành động approve và requestChanges.
     * @param token Token từ URL
     * @return Thực thể Contract hợp lệ
     */
    private Contract validateReviewToken(String token) {
        Contract contract = contractRepository.findByReviewToken(token)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND));

        if (contract.getReviewTokenExpiresAt() != null && contract.getReviewTokenExpiresAt().isBefore(LocalDateTime.now())) {
            throw new AppException(ErrorCode.INVITATION_EXPIRED);
        }

        if (contract.getStatus() != ContractStatus.PENDING_CLIENT_APPROVAL) {
            throw new AppException(ErrorCode.BAD_REQUEST);
        }
        return contract;
    }

    /**
     * Lấy file PDF để hiển thị cho client dựa trên token.
     */
    @Override
    public byte[] getReviewPdf(String token) {
        // Cho phép xem kể cả khi link hết hạn, nhưng không cho hành động
        Contract contract = contractRepository.findByReviewToken(token)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND));

        ContractDocument doc = contractDocumentRepository
                .findTopByContract_IdAndTypeOrderByVersionDesc(contract.getId(), ContractDocumentType.FILLED)
                .orElseThrow(() -> new AppException(ErrorCode.CONTRACT_DOC_NOT_FOUND));

        return storageService.load(doc.getStorageUrl());
    }

    /**
     * Xử lý khi client đồng ý với hợp đồng.
     */
    @Override
    @Transactional
    public void approveContract(String token) {
        Contract contract = validateReviewToken(token);

        log.info("Khách hàng đã đồng ý hợp đồng ID: {}. Cập nhật trạng thái thành CLIENT_APPROVED.", contract.getId());
        contract.setStatus(ContractStatus.CLIENT_APPROVED);
        contract.setReviewToken(null); // Vô hiệu hóa token để không dùng lại được
        contract.setReviewTokenExpiresAt(null);
        contractRepository.save(contract);

        // Gửi sự kiện Kafka để thông báo cho Producer
        User producer = contract.getProject().getCreator();
        User client = contract.getProject().getClient();
        if (producer != null && client != null) {
            Map<String, Object> params = Map.of(
                    "clientName", client.getFullName(),
                    "projectName", contract.getProject().getTitle()
            );

            NotificationEvent event = NotificationEvent.builder()
                    .recipient(producer.getEmail())
                    .subject("Khách hàng đã đồng ý với nội dung hợp đồng")
                    .templateCode("client-approved-contract") // Cần tạo template này
                    .param(params)
                    .build();
            kafkaTemplate.send("notification-delivery", event);
        }
    }

    /**
     * Xử lý khi client yêu cầu chỉnh sửa hợp đồng.
     */
    @Override
    @Transactional
    public void requestChanges(String token, ContractChangeRequest req) {
        Contract contract = validateReviewToken(token);

        log.info("Khách hàng yêu cầu chỉnh sửa hợp đồng ID: {}. Cập nhật trạng thái về DRAFT.", contract.getId());

        // 1. Lưu lại comment của khách hàng
        ContractComment comment = ContractComment.builder()
                .contract(contract)
                .comment(req.getComments())
                .build();
        contractCommentRepository.save(comment);

        // 2. Cập nhật trạng thái hợp đồng về DRAFT để producer sửa
        contract.setStatus(ContractStatus.DRAFT);
        contract.setReviewToken(null); // Vô hiệu hóa token
        contract.setReviewTokenExpiresAt(null);
        contractRepository.save(contract);

        // 3. Gửi sự kiện Kafka cho Producer kèm theo comment của khách hàng
        User producer = contract.getProject().getCreator();
        User client = contract.getProject().getClient();
        if (producer != null && client != null) {
            Map<String, Object> params = Map.of(
                    "clientName", client.getFullName(),
                    "projectName", contract.getProject().getTitle(),
                    "comments", req.getComments()
            );

            NotificationEvent event = NotificationEvent.builder()
                    .recipient(producer.getEmail())
                    .subject("Khách hàng yêu cầu chỉnh sửa hợp đồng")
                    .templateCode("client-requested-changes") // Cần tạo template này
                    .param(params)
                    .build();
            kafkaTemplate.send("notification-delivery", event);
        }
    }
}