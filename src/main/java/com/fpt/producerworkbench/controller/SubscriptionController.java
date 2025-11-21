package com.fpt.producerworkbench.controller;

import com.fpt.producerworkbench.dto.request.SubscriptionPurchaseRequest;
import com.fpt.producerworkbench.dto.request.SubscriptionUpgradeRequest;
import com.fpt.producerworkbench.dto.response.ApiResponse;
import com.fpt.producerworkbench.dto.response.SubscriptionActionResponse;
import com.fpt.producerworkbench.dto.response.SubscriptionStatusResponse;
import com.fpt.producerworkbench.repository.UserRepository;
import com.fpt.producerworkbench.service.ProSubscriptionService;
import com.fpt.producerworkbench.utils.SecurityUtils;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * Controller quản lý các thao tác liên quan đến subscription PRO.
 * Bao gồm: mua subscription mới, nâng cấp subscription, hủy/kích hoạt tự động gia hạn,
 * xem trạng thái subscription, và xử lý webhook từ hệ thống thanh toán.
 */
@RestController
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RequestMapping("/api/v1/subscriptions")
@Slf4j
public class SubscriptionController {

    ProSubscriptionService proSubscriptionService;
    UserRepository userRepository;

    // NOTE: userId should come from security context in real scenario
    private Long currentUserId() {
        var emailOpt = SecurityUtils.getCurrentUserLogin();
        if (emailOpt.isEmpty()) {
            throw new com.fpt.producerworkbench.exception.AppException(com.fpt.producerworkbench.exception.ErrorCode.ACCESS_DENIED);
        }
        return userRepository.findByEmail(emailOpt.get())
                .orElseThrow(() -> new com.fpt.producerworkbench.exception.AppException(com.fpt.producerworkbench.exception.ErrorCode.USER_NOT_FOUND))
                .getId();
    }

    /**
     * Mua subscription PRO mới.
     * Tạo payment order và trả về link thanh toán PayOS.
     */
    @PostMapping("/purchase")
    ApiResponse<SubscriptionActionResponse> purchase(@Valid @RequestBody SubscriptionPurchaseRequest request) {
        var result = proSubscriptionService.purchase(currentUserId(), request);
        return ApiResponse.<SubscriptionActionResponse>builder()
                .code(HttpStatus.OK.value())
                .result(result)
                .build();
    }

    /**
     * Nâng cấp subscription lên gói PRO cao hơn.
     * Tạo payment order và trả về link thanh toán PayOS.
     */
    @PostMapping("/upgrade")
    ApiResponse<SubscriptionActionResponse> upgrade(@Valid @RequestBody SubscriptionUpgradeRequest request) {
        var result = proSubscriptionService.upgrade(currentUserId(), request);
        return ApiResponse.<SubscriptionActionResponse>builder()
                .code(HttpStatus.OK.value())
                .result(result)
                .build();
    }

    /**
     * Hủy tự động gia hạn subscription.
     * Subscription sẽ hết hạn sau khi đến ngày kết thúc hiện tại.
     */
    @PostMapping("/cancel-auto-renew")
    ApiResponse<Void> cancelAutoRenew() {
        proSubscriptionService.cancelAutoRenew(currentUserId());
        return ApiResponse.<Void>builder()
                .code(HttpStatus.OK.value())
                .message("Đã hủy tự động gia hạn")
                .build();
    }

    /**
     * Kích hoạt lại tự động gia hạn subscription.
     * Subscription sẽ tự động gia hạn khi đến ngày kết thúc.
     */
    @PostMapping("/reactivate-auto-renew")
    ApiResponse<Void> reactivateAutoRenew() {
        proSubscriptionService.reactivateAutoRenew(currentUserId());
        return ApiResponse.<Void>builder()
                .code(HttpStatus.OK.value())
                .message("Đã kích hoạt lại tự động gia hạn")
                .build();
    }

    /**
     * Lấy trạng thái subscription hiện tại của user.
     * Bao gồm: trạng thái, tên gói, ngày hết hạn, trạng thái auto renew.
     */
    @GetMapping("/status")
    ApiResponse<SubscriptionStatusResponse> status() {
        var result = proSubscriptionService.getStatus(currentUserId());
        return ApiResponse.<SubscriptionStatusResponse>builder()
                .code(HttpStatus.OK.value())
                .result(result)
                .build();
    }

    /**
     * Xử lý webhook từ hệ thống thanh toán.
     * Endpoint này được hệ thống thanh toán gọi tự động, không cần authentication.
     */
    @PostMapping("/webhook")
    public void webhook(@RequestBody String body) {
        proSubscriptionService.handleWebhook(body);
    }
}


