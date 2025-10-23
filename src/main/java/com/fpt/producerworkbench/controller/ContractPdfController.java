package com.fpt.producerworkbench.controller;

import com.fpt.producerworkbench.dto.request.ContractPdfFillRequest;
import com.fpt.producerworkbench.service.ContractPdfService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/contracts/pdf")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class ContractPdfController {

    ContractPdfService contractPdfService;

    @PostMapping(value = "{projectId}/fill", produces = "application/pdf")
    public ResponseEntity<byte[]> fill(
            Authentication auth,
            @PathVariable Long projectId,
            @Valid @RequestBody ContractPdfFillRequest req,
            HttpServletRequest request
    ) {
        byte[] pdf = contractPdfService.fillTemplate(auth, projectId, req);
        return ResponseEntity.ok()
                .header("Content-Disposition", "inline; filename=contract.pdf")
                .body(pdf);
    }
}

