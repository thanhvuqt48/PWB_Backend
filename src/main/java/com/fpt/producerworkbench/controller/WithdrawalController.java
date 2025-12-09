package com.fpt.producerworkbench.controller;

import com.fpt.producerworkbench.common.WithdrawalStatus;
import com.fpt.producerworkbench.dto.request.RejectWithdrawalRequest;
import com.fpt.producerworkbench.dto.request.WithdrawalRequest;
import com.fpt.producerworkbench.dto.response.ApiResponse;
import com.fpt.producerworkbench.dto.response.BalanceResponse;
import com.fpt.producerworkbench.dto.response.WithdrawalResponse;
import com.fpt.producerworkbench.service.WithdrawalService;
import com.fpt.producerworkbench.utils.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Date;

@RestController
@RequestMapping("/api/v1/withdrawals")
@RequiredArgsConstructor
@Slf4j
public class WithdrawalController {

    private final WithdrawalService withdrawalService;
    private final SecurityUtils securityUtils;

    @GetMapping("/balance")
    public ResponseEntity<ApiResponse<BalanceResponse>> getUserBalance() {
        Long userId = securityUtils.getCurrentUserId();

        BalanceResponse response = withdrawalService.getUserBalance(userId);

        return ResponseEntity.ok(ApiResponse.<BalanceResponse>builder()
                .code(200)
                .message("Lấy số dư tài khoản thành công")
                .result(response)
                .build());
    }

    @PostMapping
    public ResponseEntity<ApiResponse<WithdrawalResponse>> createWithdrawal(
            @Valid @RequestBody WithdrawalRequest request) {

        Long userId = securityUtils.getCurrentUserId();

        WithdrawalResponse response = withdrawalService.createWithdrawal(userId, request);

        return ResponseEntity.ok(ApiResponse.<WithdrawalResponse>builder()
                .code(200)
                .message("Yêu cầu rút tiền đã được tạo thành công")
                .result(response)
                .build());
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<WithdrawalResponse>>> getUserWithdrawals(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) WithdrawalStatus status,
            @RequestParam(required = false) BigDecimal minAmount,
            @RequestParam(required = false) BigDecimal maxAmount,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Date fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Date toDate,
            @PageableDefault(size = 20) Pageable pageable) {

        Long userId = securityUtils.getCurrentUserId();

        Page<WithdrawalResponse> withdrawals;
        if (keyword != null || status != null || minAmount != null || maxAmount != null || fromDate != null || toDate != null) {
            withdrawals = withdrawalService.searchUserWithdrawals(
                    userId, keyword, status, minAmount, maxAmount, fromDate, toDate, pageable);
        } else {
            withdrawals = withdrawalService.getUserWithdrawals(userId, pageable);
        }

        return ResponseEntity.ok(ApiResponse.<Page<WithdrawalResponse>>builder()
                .code(200)
                .message("Lấy danh sách yêu cầu rút tiền thành công")
                .result(withdrawals)
                .build());
    }

    @GetMapping("/{withdrawalId}")
    public ResponseEntity<ApiResponse<WithdrawalResponse>> getWithdrawalById(
            @PathVariable Long withdrawalId) {

        Long userId = securityUtils.getCurrentUserId();

        WithdrawalResponse response = withdrawalService.getWithdrawalById(withdrawalId, userId);

        return ResponseEntity.ok(ApiResponse.<WithdrawalResponse>builder()
                .code(200)
                .message("Lấy thông tin yêu cầu rút tiền thành công")
                .result(response)
                .build());
    }

    @GetMapping("/admin/all")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<ApiResponse<Page<WithdrawalResponse>>> getAllWithdrawals(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) WithdrawalStatus status,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) BigDecimal minAmount,
            @RequestParam(required = false) BigDecimal maxAmount,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Date fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Date toDate,
            @PageableDefault(size = 20) Pageable pageable) {

        Page<WithdrawalResponse> withdrawals;
        if (keyword != null || status != null || userId != null || minAmount != null || maxAmount != null || fromDate != null || toDate != null) {
            withdrawals = withdrawalService.searchAllWithdrawals(
                    keyword, status, userId, minAmount, maxAmount, fromDate, toDate, pageable);
        } else {
            withdrawals = withdrawalService.getAllWithdrawals(pageable);
        }

        return ResponseEntity.ok(ApiResponse.<Page<WithdrawalResponse>>builder()
                .code(200)
                .message("Lấy danh sách tất cả yêu cầu rút tiền thành công")
                .result(withdrawals)
                .build());
    }

    @PutMapping("/admin/{withdrawalId}/approve")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<ApiResponse<WithdrawalResponse>> approveWithdrawal(
            @PathVariable Long withdrawalId) {

        WithdrawalResponse response = withdrawalService.approveWithdrawal(withdrawalId);

        return ResponseEntity.ok(ApiResponse.<WithdrawalResponse>builder()
                .code(200)
                .message("Xác nhận chuyển tiền thành công")
                .result(response)
                .build());
    }

    @PutMapping("/admin/{withdrawalId}/reject")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<ApiResponse<WithdrawalResponse>> rejectWithdrawal(
            @PathVariable Long withdrawalId,
            @Valid @RequestBody RejectWithdrawalRequest request) {

        WithdrawalResponse response = withdrawalService.rejectWithdrawal(withdrawalId, request);

        return ResponseEntity.ok(ApiResponse.<WithdrawalResponse>builder()
                .code(200)
                .message("Từ chối yêu cầu rút tiền thành công")
                .result(response)
                .build());
    }
}

