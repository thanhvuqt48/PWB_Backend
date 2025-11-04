package com.fpt.producerworkbench.controller;

import com.fpt.producerworkbench.dto.request.ContractAddendumPdfFillRequest;
import com.fpt.producerworkbench.service.ContractAddendumPdfService;
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
@RequestMapping("/api/v1/contracts/{contractId}/addendums/pdf")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class ContractAddendumPdfController {

    ContractAddendumPdfService service;

    @PostMapping(value = "fill", produces = "application/pdf")
    public ResponseEntity<byte[]> fill(
            Authentication auth,
            @PathVariable Long contractId,
            @Valid @RequestBody ContractAddendumPdfFillRequest req,
            HttpServletRequest http
    ) {
        byte[] pdf = service.fillAddendum(auth, contractId, req);
        return ResponseEntity.ok()
                .header("Content-Disposition", "inline; filename=addendum.pdf")
                .body(pdf);
    }
}