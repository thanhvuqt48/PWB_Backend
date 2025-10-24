package com.fpt.producerworkbench.controller;

import com.fpt.producerworkbench.dto.request.PaymentRequest;
import com.fpt.producerworkbench.dto.response.ApiResponse;
import com.fpt.producerworkbench.dto.response.PaymentResponse;
import com.fpt.producerworkbench.entity.User;
import com.fpt.producerworkbench.exception.AppException;
import com.fpt.producerworkbench.exception.ErrorCode;
import com.fpt.producerworkbench.exception.InvalidTokenException;
import com.fpt.producerworkbench.repository.UserRepository;
import com.fpt.producerworkbench.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Slf4j
public class PaymentController {

    private final PaymentService paymentService;
    private final UserRepository userRepository;

    @PostMapping("/create/projects/{projectId}/contracts/{contractId}")
    public ResponseEntity<ApiResponse<PaymentResponse>> createPayment(
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication,
            @PathVariable Long projectId,
            @PathVariable Long contractId,
            @Valid @RequestBody PaymentRequest paymentRequest) {

        if (projectId == null || projectId <= 0) {
            throw new AppException(ErrorCode.INVALID_PARAMETER_FORMAT);
        }
        if (contractId == null || contractId <= 0) {
            throw new AppException(ErrorCode.INVALID_PARAMETER_FORMAT);
        }

        log.info("Tạo thanh toán cho dự án: {}, hợp đồng: {}", projectId, contractId);

        Long userId = resolveUserId(authentication);

        PaymentResponse response = paymentService.createPayment(userId, projectId, contractId, paymentRequest);

        return ResponseEntity.ok(ApiResponse.<PaymentResponse>builder()
                .code(200)
                .message("Tạo link thanh toán thành công")
                .result(response)
                .build());
    }


    @PostMapping("/webhook")
    public ResponseEntity<String> handleWebhook(@RequestBody String body) {
        log.info("Nhận webhook từ PayOS");
        
        if (body == null || body.trim().isEmpty()) {
            log.warn("Webhook body trống hoặc null");
            return ResponseEntity.badRequest().body("Webhook body không hợp lệ");
        }
        
        paymentService.handlePaymentWebhook(body);
        return ResponseEntity.ok("OK");
    }

    private Long resolveUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new InvalidTokenException();
        }

        String id = jwt.getClaimAsString("sub");
        if (id != null && id.matches("\\d+")) return Long.valueOf(id);

        id = jwt.getClaimAsString("user_id");
        if (id != null && id.matches("\\d+")) return Long.valueOf(id);

        id = jwt.getClaimAsString("id");
        if (id != null && id.matches("\\d+")) return Long.valueOf(id);

        String email = jwt.getClaimAsString("email");
        if (email == null) {
            String sub = jwt.getClaimAsString("sub");
            if (sub != null && sub.contains("@")) email = sub;
        }
        if (email == null) {
            throw new InvalidTokenException();
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        return user.getId();
    }
}
