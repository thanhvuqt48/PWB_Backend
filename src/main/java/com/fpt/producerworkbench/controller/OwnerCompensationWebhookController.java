package com.fpt.producerworkbench.controller;

import com.fpt.producerworkbench.service.OwnerCompensationPaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Webhook endpoint for PayOS owner compensation payment
 */
@RestController
@RequestMapping("/api/v1/webhooks/owner-compensation")
@RequiredArgsConstructor
@Slf4j
public class OwnerCompensationWebhookController {
    
    private final OwnerCompensationPaymentService ownerCompensationPaymentService;
    
    /**
     * PayOS webhook endpoint
     * Được gọi khi Owner hoàn tất thanh toán đền bù Team
     */
    @PostMapping("/payos")
    public ResponseEntity<Map<String, String>> handlePayOSWebhook(
            @RequestBody Map<String, Object> payload
    ) {
        log.info("Received PayOS webhook for owner compensation: {}", payload);
        
        try {
            // Extract data from PayOS webhook
            String orderCode = extractOrderCode(payload);
            String status = extractStatus(payload);
            
            log.info("Processing webhook - OrderCode: {}, Status: {}", orderCode, status);
            
            // Process payment
            ownerCompensationPaymentService.handlePaymentWebhook(orderCode, status);
            
            return ResponseEntity.ok(Map.of(
                    "error", "0",
                    "message", "Webhook processed successfully",
                    "data", orderCode
            ));
            
        } catch (Exception e) {
            log.error("Error processing PayOS webhook", e);
            return ResponseEntity.ok(Map.of(
                    "error", "-1",
                    "message", "Error processing webhook: " + e.getMessage(),
                    "data", ""
            ));
        }
    }
    
    private String extractOrderCode(Map<String, Object> payload) {
        // PayOS webhook format may vary, adjust as needed
        Object data = payload.get("data");
        if (data instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> dataMap = (Map<String, Object>) data;
            Object orderCode = dataMap.get("orderCode");
            if (orderCode != null) {
                // If it's numeric, convert to OCP format
                if (orderCode instanceof Number) {
                    return "OCP-" + orderCode;
                }
                return orderCode.toString();
            }
        }
        throw new IllegalArgumentException("Order code not found in webhook payload");
    }
    
    private String extractStatus(Map<String, Object> payload) {
        Object data = payload.get("data");
        if (data instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> dataMap = (Map<String, Object>) data;
            Object status = dataMap.get("status");
            if (status != null) {
                return status.toString();
            }
        }
        
        // Fallback: check top level
        Object status = payload.get("status");
        if (status != null) {
            return status.toString();
        }
        
        throw new IllegalArgumentException("Status not found in webhook payload");
    }
}


