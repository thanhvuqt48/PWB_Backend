package com.fpt.producerworkbench.controller;

import com.fpt.producerworkbench.dto.request.PaymentRequest;
import com.fpt.producerworkbench.dto.response.ApiResponse;
import com.fpt.producerworkbench.dto.response.PaymentResponse;
import com.fpt.producerworkbench.dto.response.PaymentStatusResponse;
import com.fpt.producerworkbench.dto.response.PaymentLatestResponse;
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

/**
 * Controller quản lý các thao tác liên quan đến thanh toán.
 * Bao gồm: tạo link thanh toán qua PayOS, xử lý webhook từ PayOS, kiểm tra trạng thái thanh toán,
 * và lấy thông tin thanh toán mới nhất của contract.
 */
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Slf4j
public class PaymentController {

    private final PaymentService paymentService;
    private final UserRepository userRepository;

    /**
     * Tạo link thanh toán cho contract thông qua PayOS.
     * Tạo payment order và trả về link thanh toán để client có thể thanh toán.
     */
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

    /**
     * Tạo link thanh toán cho phụ lục hợp đồng thông qua PayOS.
     * Thanh toán toàn bộ số tiền trong phụ lục (numOfMoney) cho dù là FULL hay MILESTONE.
     * Tạo payment order và trả về link thanh toán để client có thể thanh toán.
     */
    @PostMapping("/create/projects/{projectId}/contracts/{contractId}/addendum")
    public ResponseEntity<ApiResponse<PaymentResponse>> createAddendumPayment(
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

        log.info("Tạo thanh toán phụ lục cho dự án: {}, hợp đồng: {}", projectId, contractId);

        Long userId = resolveUserId(authentication);

        PaymentResponse response = paymentService.createAddendumPayment(userId, projectId, contractId, paymentRequest);

        return ResponseEntity.ok(ApiResponse.<PaymentResponse>builder()
                .code(200)
                .message("Tạo link thanh toán phụ lục thành công")
                .result(response)
                .build());
    }

    /**
     * Xử lý webhook từ PayOS khi có cập nhật về trạng thái thanh toán.
     * Endpoint này được PayOS gọi tự động, không cần authentication.
     */
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

    /**
     * Lấy trạng thái thanh toán theo orderCode.
     * Không yêu cầu authentication, ai cũng có thể kiểm tra trạng thái thanh toán.
     */
    @GetMapping("/status/{orderCode}")
    public ResponseEntity<ApiResponse<PaymentStatusResponse>> getPaymentStatus(@PathVariable String orderCode) {
        var result = paymentService.getPaymentStatus(orderCode);
        return ResponseEntity.ok(
                ApiResponse.<PaymentStatusResponse>builder()
                        .code(200)
                        .result(result)
                        .build()
        );
    }

    /**
     * Lấy thông tin thanh toán mới nhất của contract.
     * Yêu cầu đăng nhập và có quyền truy cập contract.
     */
    @GetMapping("/projects/{projectId}/contracts/{contractId}/latest")
    public ResponseEntity<ApiResponse<PaymentLatestResponse>> getLatestPayment(
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication,
            @PathVariable Long projectId,
            @PathVariable Long contractId
    ) {
        Long userId = resolveUserId(authentication);
        var result = paymentService.getLatestPaymentByContract(userId, projectId, contractId);
        return ResponseEntity.ok(
                ApiResponse.<PaymentLatestResponse>builder()
                        .code(200)
                        .result(result)
                        .build()
        );
    }

    /**
     * Lấy thông tin thanh toán mới nhất của phụ lục hợp đồng.
     * Yêu cầu đăng nhập và có quyền truy cập contract.
     */
    @GetMapping("/projects/{projectId}/contracts/{contractId}/addendum/latest")
    public ResponseEntity<ApiResponse<PaymentLatestResponse>> getLatestAddendumPayment(
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication,
            @PathVariable Long projectId,
            @PathVariable Long contractId
    ) {
        Long userId = resolveUserId(authentication);
        var result = paymentService.getLatestPaymentByAddendum(userId, projectId, contractId);
        return ResponseEntity.ok(
                ApiResponse.<PaymentLatestResponse>builder()
                        .code(200)
                        .result(result)
                        .build()
        );
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
