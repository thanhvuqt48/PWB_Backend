package com.fpt.producerworkbench.controller;

import com.fpt.producerworkbench.common.ContractDocumentType;
import com.fpt.producerworkbench.dto.response.ApiResponse;
import com.fpt.producerworkbench.entity.Contract;
import com.fpt.producerworkbench.exception.AppException;
import com.fpt.producerworkbench.exception.ErrorCode;
import com.fpt.producerworkbench.repository.ContractDocumentRepository;
import com.fpt.producerworkbench.repository.ContractRepository;
import com.fpt.producerworkbench.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
public class ProjectContractController {

    private final ContractRepository contractRepository;
    private final ContractDocumentRepository contractDocumentRepository;
    private final FileStorageService fileStorageService;

    @GetMapping("/{projectId}/contract")
    public ApiResponse<Map<String, Object>> getContractByProject(@PathVariable Long projectId) {
        Contract c = contractRepository.findByProjectId(projectId)
                .orElseThrow(() -> new AppException(ErrorCode.CONTRACT_NOT_FOUND));

        var resp = new HashMap<String, Object>();
        resp.put("id", c.getId());
        resp.put("status", c.getStatus());
        resp.put("signnowStatus", c.getSignnowStatus());

        // Only return one documentUrl: prefer SIGNED if exists else FILLED
        var signed = contractDocumentRepository
                .findTopByContract_IdAndTypeOrderByVersionDesc(c.getId(), ContractDocumentType.SIGNED)
                .orElse(null);
        if (signed != null) {
            resp.put("documentVersion", signed.getVersion());
            resp.put("documentType", "SIGNED");
            resp.put("documentUrl", fileStorageService.generatePresignedUrl(signed.getStorageUrl(), false, null));
        } else {
            var filled = contractDocumentRepository
                    .findTopByContract_IdAndTypeOrderByVersionDesc(c.getId(), ContractDocumentType.FILLED)
                    .orElse(null);
            if (filled != null) {
                resp.put("documentVersion", filled.getVersion());
                resp.put("documentType", "FILLED");
                resp.put("documentUrl", fileStorageService.generatePresignedUrl(filled.getStorageUrl(), false, null));
            }
        }

        return ApiResponse.<Map<String, Object>>builder().code(200).result(resp).build();
    }
}


