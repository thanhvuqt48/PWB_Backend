package com.fpt.producerworkbench.controller;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fpt.producerworkbench.entity.Contract;
import com.fpt.producerworkbench.repository.ContractRepository;
import com.fpt.producerworkbench.service.ContractSigningService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/webhooks/signnow")
@RequiredArgsConstructor
@Slf4j
public class SignNowWebhookController {

    private final ContractSigningService contractSigningService;
    private final ContractRepository contractRepository;

    /**
     * Lớp DTO để hứng dữ liệu (payload) mà SignNow gửi về.
     * Cần khớp với cấu trúc JSON của SignNow event.
     */
    @Data
    public static class SignNowEvent {
        @JsonProperty("document_id")
        private String documentId;

        @JsonProperty("event")
        private String event;

        // Có thể thêm các trường khác nếu cần, ví dụ: status, metadata...
    }

    /**
     * Endpoint để nhận các sự kiện từ SignNow.
     * @param event Dữ liệu sự kiện được SignNow gửi đến.
     */
    @PostMapping
    public ResponseEntity<Void> handleSignNowEvent(@RequestBody SignNowEvent event) {
        log.info("Nhận được webhook từ SignNow cho document ID: {}, event: {}", event.documentId, event.event);

        // Chỉ xử lý khi sự kiện là "document_complete" (hoặc tên sự kiện tương ứng bạn cấu hình)
        // Đây là sự kiện báo rằng tất cả các bên đã ký xong.
        if ("document_complete".equalsIgnoreCase(event.event)) {

            // Tìm hợp đồng trong CSDL của bạn bằng signnowDocumentId
            Contract contract = contractRepository.findBySignnowDocumentId(event.documentId)
                    .orElse(null);

            if (contract != null) {
                log.info("Tìm thấy hợp đồng ID: {}. Bắt đầu quá trình tải về và lưu trữ file đã ký.", contract.getId());
                try {
                    // Kích hoạt service của Giai đoạn 5
                    contractSigningService.saveSignedAndComplete(contract.getId(), true);
                    log.info("Hoàn tất xử lý webhook cho hợp đồng ID: {}", contract.getId());
                } catch (Exception e) {
                    log.error("Lỗi khi xử lý webhook cho hợp đồng ID: {}. Lỗi: {}", contract.getId(), e.getMessage());
                    // Trả về lỗi để SignNow có thể thử lại (nếu được cấu hình)
                    return ResponseEntity.internalServerError().build();
                }
            } else {
                log.warn("Không tìm thấy hợp đồng nào trong hệ thống tương ứng với SignNow Document ID: {}", event.documentId);
            }
        }

        // Luôn trả về 200 OK để SignNow biết rằng bạn đã nhận được webhook thành công.
        // Nếu không, SignNow có thể sẽ gửi lại webhook nhiều lần.
        return ResponseEntity.ok().build();
    }
}