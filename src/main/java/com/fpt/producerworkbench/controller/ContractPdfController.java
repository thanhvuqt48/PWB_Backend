package com.fpt.producerworkbench.controller;

import com.fpt.producerworkbench.dto.request.ContractPdfFillRequest;
import com.fpt.producerworkbench.service.ContractPdfService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/contracts/{contractId}")
@RequiredArgsConstructor
public class ContractPdfController {

    private final ContractPdfService contractPdfService;

    @PostMapping(value = "/send-for-review", produces = "application/pdf")
    public ResponseEntity<byte[]> generateAndSendReview(
            @PathVariable Long contractId,
            @Valid @RequestBody ContractPdfFillRequest req,
            Authentication auth
    ) {
        byte[] pdf = contractPdfService.generateAndSendReviewPdf(auth, contractId, req);

        return ResponseEntity.ok()
                .header("Content-Disposition", "inline; filename=contract_review.pdf")
                .body(pdf);
    }
}