package com.fpt.producerworkbench.service.impl;

import com.fpt.producerworkbench.common.ContractStatus;
import com.fpt.producerworkbench.configuration.SignNowClient;
import com.fpt.producerworkbench.entity.Contract;
import com.fpt.producerworkbench.entity.User;
import com.fpt.producerworkbench.exception.AppException;
import com.fpt.producerworkbench.exception.ErrorCode;
import com.fpt.producerworkbench.repository.ContractRepository;
import com.fpt.producerworkbench.service.ContractInviteService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ContractInviteServiceImpl implements ContractInviteService {

    private final ContractRepository contractRepository;
    private final SignNowClient signNowClient;
    // Có thể inject các service khác như StorageService hoặc một service tạo PDF mới

    @Override
    @Transactional
    public void sendSigningInvitation(Long contractId) {
        log.info("Bắt đầu quy trình mời ký chính thức cho hợp đồng ID: {}", contractId);

        // 1. TÌM HỢP ĐỒNG VÀ KIỂM TRA TRẠNG THÁI TIÊN QUYẾT
        Contract c = contractRepository.findByIdWithDetails(contractId)
                .orElseThrow(() -> new AppException(ErrorCode.CONTRACT_NOT_FOUND));

        if (c.getStatus() != ContractStatus.CLIENT_APPROVED) {
            throw new AppException(ErrorCode.BAD_REQUEST);
        }

        // 2. TẠO HOẶC LẤY FILE PDF PHIÊN BẢN CUỐI CÙNG
        // Logic này đảm bảo file PDF được ký là phiên bản cuối cùng, không phải file nháp
        // TODO: Viết logic để tạo file PDF cuối cùng (không có watermark) dựa trên dữ liệu từ `c`.
        // Ví dụ: byte[] finalPdfBytes = pdfGenerationService.generateFinal(c);
        log.warn("Đang sử dụng placeholder cho việc tạo PDF cuối cùng cho hợp đồng ID: {}", contractId);
        byte[] finalPdfBytes = new byte[0]; // Placeholder

        // 3. UPLOAD TÀI LIỆU LÊN SIGNNOW
        // Sử dụng lại logic upload document mạnh mẽ của bạn
        String docId = signNowClient.uploadDocumentWithFieldExtract(finalPdfBytes, "contract-" + contractId + ".pdf");
        c.setSignnowDocumentId(docId);

        // 4. CHUẨN BỊ LỜI MỜI KÝ (FIELD INVITE)
        User producer = c.getProject().getCreator();
        User client = c.getProject().getClient();

        if (client == null) {
            throw new AppException(ErrorCode.BAD_REQUEST);
        }

        // Giả sử template PDF trên SignNow có các vai trò (roles) được đặt tên là "producer" và "client"
        Map<String, String> roleIdMap = signNowClient.getRoleIdMap(c.getSignnowDocumentId());
        String producerRoleId = roleIdMap.get("producer");
        String clientRoleId = roleIdMap.get("client");

        if (producerRoleId == null || clientRoleId == null) {
            throw new AppException(ErrorCode.SIGNNOW_DOC_HAS_NO_FIELDS);
        }

        List<Map<String, Object>> signers = new ArrayList<>();

        // Người ký 1: Producer
        Map<String, Object> producerSigner = new HashMap<>();
        producerSigner.put("email", producer.getEmail());
        producerSigner.put("role_id", producerRoleId);
        producerSigner.put("order", 1); // Ký trước
        signers.add(producerSigner);

        // Người ký 2: Client
        Map<String, Object> clientSigner = new HashMap<>();
        clientSigner.put("email", client.getEmail());
        clientSigner.put("role_id", clientRoleId);
        clientSigner.put("order", 2); // Ký sau
        signers.add(clientSigner);

        // Gửi lời mời ký tuần tự
        signNowClient.createFieldInvite(c.getSignnowDocumentId(), signers, true, null);

        // 5. CẬP NHẬT TRẠNG THÁI CUỐI CÙNG
        c.setStatus(ContractStatus.OUT_FOR_SIGNATURE);
        c.setSignnowStatus(ContractStatus.OUT_FOR_SIGNATURE); // Cập nhật cả 2 trạng thái
        contractRepository.save(c);

        log.info("Đã gửi lời mời ký thành công cho hợp đồng ID: {}. Trạng thái mới: OUT_FOR_SIGNATURE", contractId);
    }
}