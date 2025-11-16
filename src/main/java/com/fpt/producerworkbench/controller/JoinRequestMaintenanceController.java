package com.fpt.producerworkbench.controller;

import com.fpt.producerworkbench.dto.response.ApiResponse;
import com.fpt.producerworkbench.service.impl.JoinRequestRedisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/join-requests")
@RequiredArgsConstructor
@Slf4j
public class JoinRequestMaintenanceController {

    private final JoinRequestRedisService redisService;

    /**
     * Clean up all expired or corrupted join requests
     * POST /api/admin/join-requests/cleanup
     */
    @PostMapping("/cleanup")
    public ResponseEntity<ApiResponse<String>> cleanupJoinRequests() {
        log.info("🧹 Manual cleanup triggered");
        
        int cleanedCount = redisService.cleanupExpiredRequests();
        
        String message = String.format("Cleaned up %d join requests", cleanedCount);
        log.info("✅ {}", message);
        
        return ResponseEntity.ok(ApiResponse.<String>builder()
                .code(1000)
                .message(message)
                .result(String.format("%d requests cleaned", cleanedCount))
                .build());
    }
}
