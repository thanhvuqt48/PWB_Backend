package com.fpt.producerworkbench.controller;

import com.fpt.producerworkbench.dto.response.ApiResponse;
import com.fpt.producerworkbench.service.impl.SignNowWebhookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/signnow/webhook")
@RequiredArgsConstructor
@Slf4j
public class SignNowWebhookController {

    private final SignNowWebhookService webhookService;

    // SignNow có thể POST JSON khi document completed/declined...
    @PostMapping
    public ResponseEntity<ApiResponse<String>> handle(@RequestBody Map<String, Object> payload) {
        log.info("[Webhook] SignNow payload: {}", payload);
        webhookService.handle(payload);
        return ResponseEntity.ok(ApiResponse.<String>builder().code(200).result("ok").build());
    }
}


