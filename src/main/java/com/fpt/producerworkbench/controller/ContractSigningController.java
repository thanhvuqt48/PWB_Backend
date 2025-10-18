package com.fpt.producerworkbench.controller;

import com.fpt.producerworkbench.dto.response.ApiResponse;
import com.fpt.producerworkbench.dto.response.SignedContractResponse;
import com.fpt.producerworkbench.exception.AppException;
import com.fpt.producerworkbench.exception.ErrorCode;
import com.fpt.producerworkbench.repository.ContractDocumentRepository;
import com.fpt.producerworkbench.service.ContractSigningService;
import com.fpt.producerworkbench.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.fpt.producerworkbench.common.ContractDocumentType;



/**
 * Controller xử lý việc lưu bản ký cuối của hợp đồng
 * 
 * CHÚ Ý: Chức năng webhook tự động đã bị vô hiệu hóa.
 * Chỉ sử dụng API thủ công này để lưu bản ký cuối.
 * 
 * Endpoint: POST /api/v1/contracts/{id}/signed
 */
@RestController
@RequestMapping("/api/v1/contracts")
@RequiredArgsConstructor
public class ContractSigningController {

    private final ContractSigningService contractSigningService;
    private final ContractDocumentRepository contractDocumentRepository;
    private final FileStorageService fileStorageService;

    @PostMapping("/{id}/signed")
    public ApiResponse<SignedContractResponse> saveSignedAndComplete(
            @PathVariable Long id,
            @RequestParam(name = "withHistory", defaultValue = "false") boolean withHistory
    ) {
        var result = contractSigningService.saveSignedAndComplete(id, withHistory);
        return ApiResponse.<SignedContractResponse>builder().code(200).result(result).build();
    }

    @GetMapping("/{id}/signed/file")
    public ResponseEntity<Void> viewSignedPdf(@PathVariable Long id) {
        var doc = contractDocumentRepository
                .findTopByContract_IdAndTypeOrderByVersionDesc(id, ContractDocumentType.SIGNED)
                .orElseThrow(() -> new AppException(ErrorCode.CONTRACT_DOC_NOT_FOUND));

        String url = fileStorageService.generatePresignedUrl(doc.getStorageUrl(), false, null);
        return ResponseEntity.status(302).header(HttpHeaders.LOCATION, url).build();
    }
}
