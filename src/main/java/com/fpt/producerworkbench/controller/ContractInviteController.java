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

    @PostMapping("/{id}/invites")
    public ApiResponse<StartSigningResponse> invite(@PathVariable Long id,
                                                    @RequestBody ContractInviteRequest req,
                                                    Authentication auth) {
        var result = contractInviteService.invite(auth, id, req);
        return ApiResponse.<StartSigningResponse>builder().code(200).result(result).build();
    }
}
