package com.fpt.producerworkbench.controller;

import com.fpt.producerworkbench.dto.request.ContractAddendumPdfFillRequest;
import com.fpt.producerworkbench.dto.request.ContractInviteRequest;
import com.fpt.producerworkbench.dto.response.ApiResponse;
import com.fpt.producerworkbench.dto.response.StartSigningResponse;
import com.fpt.producerworkbench.service.ContractAddendumInviteService;
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
@RequestMapping("/api/v1/contracts/{contractId}/addendum")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class ContractAddendumController {

    ContractAddendumPdfService pdfService;
    ContractAddendumInviteService inviteService;

    @PostMapping(value = "/pdf/fill", produces = "application/pdf")
    public ResponseEntity<byte[]> fillPdf(
            Authentication auth,
            @PathVariable Long contractId,
            @Valid @RequestBody ContractAddendumPdfFillRequest req,
            HttpServletRequest http
    ) {
        byte[] pdf = pdfService.fillAddendum(auth, contractId, req);
        return ResponseEntity.ok()
                .header("Content-Disposition", "inline; filename=addendum.pdf")
                .body(pdf);
    }

    @PostMapping("/invites")
    public ApiResponse<StartSigningResponse> invite(
            @PathVariable Long contractId,
            @RequestBody(required = false) ContractInviteRequest req,
            Authentication auth
    ) {
        if (req == null) req = new ContractInviteRequest();
        var result = inviteService.inviteAddendum(auth, contractId, req);
        return ApiResponse.<StartSigningResponse>builder().code(200).result(result).build();
    }
}

