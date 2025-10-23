package com.fpt.producerworkbench.controller;

import com.fpt.producerworkbench.dto.response.ApiResponse;
import com.fpt.producerworkbench.dto.response.ContractPermissionResponse;
import com.fpt.producerworkbench.service.ContractPermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
public class ContractPermissionController {

    private final ContractPermissionService contractPermissionService;

    @GetMapping("/{projectId}/contract-permissions")
    public ApiResponse<ContractPermissionResponse> getContractPermissions(
            @PathVariable Long projectId,
            Authentication auth) {
        var permissions = contractPermissionService.checkContractPermissions(auth, projectId);
        return ApiResponse.<ContractPermissionResponse>builder()
                .code(200)
                .result(permissions)
                .build();
    }
}
