package com.fpt.producerworkbench.controller;

import com.fpt.producerworkbench.dto.request.ContractCreateRequest;
import com.fpt.producerworkbench.dto.response.ApiResponse;
import com.fpt.producerworkbench.entity.Contract;
import com.fpt.producerworkbench.service.ContractService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/contract")
@RequiredArgsConstructor
public class ContractController {

    private final ContractService contractService;

    @PostMapping
    public ApiResponse<Contract> createDraftContract(
            @PathVariable Long projectId,
            @Valid @RequestBody ContractCreateRequest req,
            Authentication auth) {

        Contract createdContract = contractService.createDraftContract(auth, projectId, req);

        return ApiResponse.<Contract>builder()
                .code(200)
                .message("Tạo hợp đồng nháp thành công.")
                .result(createdContract)
                .build();
    }
}