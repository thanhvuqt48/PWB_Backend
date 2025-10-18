package com.fpt.producerworkbench.controller;

import com.fpt.producerworkbench.dto.response.ApiResponse;
// import com.fpt.producerworkbench.service.impl.SignNowWebhookService; // Đã bị comment vì webhook bị vô hiệu hóa
// import lombok.RequiredArgsConstructor; // Không cần thiết
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/signnow/webhook")
@Slf4j
public class SignNowWebhookController {

    // private final SignNowWebhookService webhookService; // Đã bị comment vì webhook bị vô hiệu hóa
    
    // Constructor không cần thiết vì không có field nào

    // SignNow có thể POST JSON khi document completed/declined...
    // CHỨC NĂNG WEBHOOK TỰ ĐỘNG ĐÃ BỊ VÔ HIỆU HÓA
    @PostMapping
    public ResponseEntity<ApiResponse<String>> handle(@RequestBody Map<String, Object> payload) {
        log.info("[Webhook] SignNow payload received but webhook auto-save is DISABLED: {}", payload);
        // webhookService.handle(payload); // Đã bị comment để vô hiệu hóa
        return ResponseEntity.ok(ApiResponse.<String>builder().code(200).result("webhook-disabled").build());
    }
}


