package com.fpt.producerworkbench.controller;

import com.fpt.producerworkbench.dto.response.ApiResponse;
import com.fpt.producerworkbench.dto.response.SignedContractResponse;
import com.fpt.producerworkbench.exception.AppException;
import com.fpt.producerworkbench.exception.ErrorCode;
import com.fpt.producerworkbench.repository.ContractDocumentRepository;
import com.fpt.producerworkbench.service.ContractSigningService;
import com.fpt.producerworkbench.service.StorageService;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.fpt.producerworkbench.dto.response.*;
import com.fpt.producerworkbench.common.ContractDocumentType;

import java.nio.file.Path;


@RestController
@RequestMapping("/api/v1/contracts")
@RequiredArgsConstructor
public class ContractSigningController {

    private final ContractSigningService contractSigningService;
    private final ContractDocumentRepository contractDocumentRepository;
    private final StorageService  storageService;

    @PostMapping("/{id}/signed")
    public ApiResponse<SignedContractResponse> saveSignedAndComplete(
            @PathVariable Long id,
            @RequestParam(name = "withHistory", defaultValue = "false") boolean withHistory
    ) {
        var result = contractSigningService.saveSignedAndComplete(id, withHistory);
        return ApiResponse.<SignedContractResponse>builder().code(200).result(result).build();
    }

    @GetMapping("/{id}/signed/file")
    public ResponseEntity<Resource> viewSignedPdf(@PathVariable Long id) {
        var doc = contractDocumentRepository
                .findTopByContract_IdAndTypeOrderByVersionDesc(id, ContractDocumentType.SIGNED)
                .orElseThrow(() -> new AppException(ErrorCode.CONTRACT_DOC_NOT_FOUND));

        byte[] bytes = storageService.read(doc.getStorageUrl());
        String filename = Path.of(doc.getStorageUrl()).getFileName().toString();

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                .body(new ByteArrayResource(bytes));
    }
}
