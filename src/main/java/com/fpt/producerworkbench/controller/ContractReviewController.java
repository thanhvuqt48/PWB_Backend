package com.fpt.producerworkbench.controller;

import com.fpt.producerworkbench.dto.request.ContractChangeRequest;
import com.fpt.producerworkbench.dto.response.ApiResponse;
import com.fpt.producerworkbench.service.ContractReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/contracts/review")
@RequiredArgsConstructor
public class ContractReviewController {

    private final ContractReviewService contractReviewService;

    @GetMapping(value = "/{token}", produces = "application/pdf")
    public ResponseEntity<byte[]> getReviewPdf(@PathVariable String token) {
        byte[] pdf = contractReviewService.getReviewPdf(token);
        return ResponseEntity.ok()
                .header("Content-Disposition", "inline; filename=contract_review.pdf")
                .body(pdf);
    }

    @PostMapping("/{token}/approve")
    public ApiResponse<Void> approveContract(@PathVariable String token) {
        contractReviewService.approveContract(token);
        return ApiResponse.<Void>builder()
                .code(200)
                .message("Hợp đồng đã được xác nhận thành công.")
                .build();
    }

    @PostMapping("/{token}/request-changes")
    public ApiResponse<Void> requestChanges(
            @PathVariable String token,
            @Valid @RequestBody ContractChangeRequest req) {
        contractReviewService.requestChanges(token, req);
        return ApiResponse.<Void>builder()
                .code(200)
                .message("Yêu cầu chỉnh sửa của bạn đã được gửi đi.")
                .build();
    }
}