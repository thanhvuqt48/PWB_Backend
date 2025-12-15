package com.fpt.producerworkbench.controller;

import com.fpt.producerworkbench.dto.response.*;
import com.fpt.producerworkbench.service.ContractTerminationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * API endpoints for contract termination
 */
@RestController
@RequestMapping("/api/v1/contracts/{contractId}/termination")
@RequiredArgsConstructor
public class ContractTerminationController {
    
    private final ContractTerminationService terminationService;
    
    /**
     * Preview tính toán trước khi chấm dứt hợp đồng
     * Chỉ cần contractId, tự động detect terminatedBy từ user đăng nhập
     * Trả về ClientTerminationPreviewResponse hoặc OwnerTerminationPreviewResponse
     */
    @GetMapping("/preview")
    public ResponseEntity<Object> previewTermination(
            @PathVariable Long contractId,
            Authentication auth
    ) {
        Object response = terminationService.previewTermination(contractId, auth);
        return ResponseEntity.ok(response);
    }
    
    /**
     * Chấm dứt hợp đồng
     */
    @PostMapping
    public ResponseEntity<TerminationResponse> terminateContract(
            @PathVariable Long contractId,
            @Valid @RequestBody TerminationRequest request,
            Authentication auth
    ) {
        TerminationResponse response = terminationService
                .terminateContract(contractId, request, auth);
        return ResponseEntity.ok(response);
    }
    
    /**
     * Lấy chi tiết chấm dứt hợp đồng
     */
    @GetMapping
    public ResponseEntity<TerminationDetailResponse> getTerminationDetail(
            @PathVariable Long contractId,
            Authentication auth
    ) {
        TerminationDetailResponse response = terminationService
                .getTerminationDetail(contractId, auth);
        return ResponseEntity.ok(response);
    }
}


