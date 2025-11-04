package com.fpt.producerworkbench.controller;

import com.fpt.producerworkbench.dto.request.ContractInviteRequest;
import com.fpt.producerworkbench.dto.response.ApiResponse;
import com.fpt.producerworkbench.dto.response.StartSigningResponse;
import com.fpt.producerworkbench.service.ContractAddendumInviteService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/contracts/{contractId}/addendum")
@RequiredArgsConstructor
public class ContractAddendumInviteController {

    private final ContractAddendumInviteService addendumInviteService;

    @PostMapping("/invites")
    public ApiResponse<StartSigningResponse> invite(@PathVariable Long contractId,
                                                    @RequestBody(required = false) ContractInviteRequest req,
                                                    Authentication auth) {
        if (req == null) req = new ContractInviteRequest();
        var result = addendumInviteService.inviteAddendum(auth, contractId, req);
        return ApiResponse.<StartSigningResponse>builder().code(200).result(result).build();
    }
}
