package com.fpt.producerworkbench.controller;

import com.fpt.producerworkbench.dto.request.FcmTokenRequest;
import com.fpt.producerworkbench.dto.response.ApiResponse;
import com.fpt.producerworkbench.service.PushNotificationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controller for FCM Token management (Push Notifications)
 */
@RestController
@RequestMapping("/api/v1/fcm")
public class FcmTokenController {

    private final PushNotificationService pushNotificationService;

    public FcmTokenController(PushNotificationService pushNotificationService) {
        this.pushNotificationService = pushNotificationService;
    }

    /**
     * Register FCM token for push notifications
     * Called by frontend when user grants notification permission
     */
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<Void>> registerToken(@Valid @RequestBody FcmTokenRequest request) {
        pushNotificationService.registerToken(request);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .code(200)
                .message("FCM token registered successfully")
                .build());
    }

    /**
     * Unregister/Remove FCM token
     * Called when user logs out or revokes notification permission
     */
    @DeleteMapping("/unregister")
    public ResponseEntity<ApiResponse<Void>> unregisterToken(@RequestParam String token) {
        pushNotificationService.unregisterToken(token);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .code(200)
                .message("FCM token unregistered successfully")
                .build());
    }
}
