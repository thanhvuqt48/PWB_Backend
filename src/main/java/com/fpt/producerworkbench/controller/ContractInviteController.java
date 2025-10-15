package com.fpt.producerworkbench.controller;

import com.fpt.producerworkbench.dto.request.ContractInviteRequest;
import com.fpt.producerworkbench.dto.response.ApiResponse;
import com.fpt.producerworkbench.dto.response.StartSigningResponse;
import com.fpt.producerworkbench.service.ContractInviteService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/contracts")
@RequiredArgsConstructor
public class ContractInviteController {

    private final ContractInviteService contractInviteService;

    @PostMapping
    public ApiResponse<Void> inviteToSign(@PathVariable Long contractId) {
        contractInviteService.sendSigningInvitation(contractId);
        return ApiResponse.<Void>builder()
                .code(200)
                .message("Lời mời ký hợp đồng đã được gửi đi thành công qua SignNow.")
                .build();
    }
}
