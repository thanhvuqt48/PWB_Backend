package com.fpt.producerworkbench.controller;

import com.fpt.producerworkbench.dto.request.AddBankAccountRequest;
import com.fpt.producerworkbench.dto.request.SendBankAccountOtpRequest;
import com.fpt.producerworkbench.dto.response.ApiResponse;
import com.fpt.producerworkbench.dto.response.UserBankResponse;
import com.fpt.producerworkbench.service.UserBankService;
import com.fpt.producerworkbench.utils.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/user-banks")
@RequiredArgsConstructor
@Slf4j
public class UserBankController {

    private final UserBankService userBankService;
    private final SecurityUtils securityUtils;

    @PostMapping("/send-otp")
    public ResponseEntity<ApiResponse<Void>> sendBankAccountOtp(
            @Valid @RequestBody SendBankAccountOtpRequest request) {
        
        Long userId = securityUtils.getCurrentUserId();
        
        userBankService.sendBankAccountOtp(userId, request);
        
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .code(200)
                .message("Mã OTP đã được gửi thành công tới email")
                .build());
    }

    @PostMapping
    public ResponseEntity<ApiResponse<UserBankResponse>> addBankAccount(
            @Valid @RequestBody AddBankAccountRequest request) {
        
        Long userId = securityUtils.getCurrentUserId();
        
        UserBankResponse response = userBankService.addBankAccount(userId, request);
        
        return ResponseEntity.ok(ApiResponse.<UserBankResponse>builder()
                .code(200)
                .message("Thêm thông tin ngân hàng thành công")
                .result(response)
                .build());
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<UserBankResponse>>> getUserBanks() {
        Long userId = securityUtils.getCurrentUserId();
        
        List<UserBankResponse> banks = userBankService.getUserBanks(userId);
        
        return ResponseEntity.ok(ApiResponse.<List<UserBankResponse>>builder()
                .code(200)
                .message("Lấy danh sách ngân hàng thành công")
                .result(banks)
                .build());
    }

    @GetMapping("/{bankAccountId}")
    public ResponseEntity<ApiResponse<UserBankResponse>> getUserBankById(
            @PathVariable Long bankAccountId) {
        
        Long userId = securityUtils.getCurrentUserId();
        
        UserBankResponse response = userBankService.getUserBankById(userId, bankAccountId);
        
        return ResponseEntity.ok(ApiResponse.<UserBankResponse>builder()
                .code(200)
                .message("Lấy thông tin ngân hàng thành công")
                .result(response)
                .build());
    }

    @DeleteMapping("/{bankAccountId}")
    public ResponseEntity<ApiResponse<Void>> deleteUserBank(
            @PathVariable Long bankAccountId) {
        
        Long userId = securityUtils.getCurrentUserId();
        
        userBankService.deleteUserBank(userId, bankAccountId);
        
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .code(200)
                .message("Xóa thông tin ngân hàng thành công")
                .build());
    }
}

