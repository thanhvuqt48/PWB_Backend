package com.fpt.producerworkbench.controller;

import com.fpt.producerworkbench.exception.AppException;
import com.fpt.producerworkbench.exception.ErrorCode;
import com.fpt.producerworkbench.repository.ContractDocumentRepository;
import com.fpt.producerworkbench.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.fpt.producerworkbench.common.ContractDocumentType;



/**
 * Controller xử lý việc xem file hợp đồng đã ký
 */
@RestController
@RequestMapping("/api/v1/contracts")
@RequiredArgsConstructor
public class ContractSigningController {

    private final ContractDocumentRepository contractDocumentRepository;
    private final FileStorageService fileStorageService;

    @GetMapping("/{id}/signed/file")
    public ResponseEntity<Void> viewSignedPdf(@PathVariable Long id) {
        var doc = contractDocumentRepository
                .findTopByContract_IdAndTypeOrderByVersionDesc(id, ContractDocumentType.SIGNED)
                .orElseThrow(() -> new AppException(ErrorCode.CONTRACT_DOC_NOT_FOUND));

        String url = fileStorageService.generatePresignedUrl(doc.getStorageUrl(), false, null);
        return ResponseEntity.status(302).header(HttpHeaders.LOCATION, url).build();
    }
}
