package com.fpt.producerworkbench.controller;

import com.fpt.producerworkbench.dto.response.ApiResponse;
import com.fpt.producerworkbench.dto.response.BankResponse;
import com.fpt.producerworkbench.service.BankService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/banks")
@RequiredArgsConstructor
@Slf4j
public class BankController {

    private final BankService bankService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<BankResponse>>> getAllBanks(
            @RequestParam(required = false) String keyword) {

        List<BankResponse> bankResponses;

        if (StringUtils.hasText(keyword)) {
            bankResponses = bankService.searchBanks(keyword);
            return ResponseEntity.ok(ApiResponse.<List<BankResponse>>builder()
                    .code(200)
                    .message("Tìm kiếm ngân hàng thành công")
                    .result(bankResponses)
                    .build());
        }

        bankResponses = bankService.getAllBanks();
        return ResponseEntity.ok(ApiResponse.<List<BankResponse>>builder()
                .code(200)
                .message("Lấy danh sách ngân hàng thành công")
                .result(bankResponses)
                .build());
    }

    @GetMapping("/transfer-supported")
    public ResponseEntity<ApiResponse<List<BankResponse>>> getTransferSupportedBanks() {
        List<BankResponse> bankResponses = bankService.getTransferSupportedBanks();

        return ResponseEntity.ok(ApiResponse.<List<BankResponse>>builder()
                .code(200)
                .message("Lấy danh sách ngân hàng hỗ trợ chuyển khoản thành công")
                .result(bankResponses)
                .build());
    }
}
