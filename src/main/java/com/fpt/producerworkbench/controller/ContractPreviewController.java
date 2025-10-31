package com.fpt.producerworkbench.controller;

import com.fpt.producerworkbench.common.ContractDocumentType;
import com.fpt.producerworkbench.entity.Contract;
import com.fpt.producerworkbench.entity.Project;
import com.fpt.producerworkbench.exception.AppException;
import com.fpt.producerworkbench.exception.ErrorCode;
import com.fpt.producerworkbench.repository.ContractDocumentRepository;
import com.fpt.producerworkbench.repository.ContractRepository;
import com.fpt.producerworkbench.service.FileStorageService;
import com.fpt.producerworkbench.service.ContractPermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/contracts")
@RequiredArgsConstructor
public class ContractPreviewController {

    private final ContractRepository contractRepository;
    private final ContractDocumentRepository contractDocumentRepository;
    private final FileStorageService fileStorageService;
    private final ContractPermissionService contractPermissionService;

    private void ensureCanViewFilled(Authentication auth, Long contractId) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new AppException(ErrorCode.CONTRACT_NOT_FOUND));
        Project project = contract.getProject();
        if (project == null) {
            throw new AppException(ErrorCode.PROJECT_NOT_FOUND);
        }

        var permissions = contractPermissionService.checkContractPermissions(auth, project.getId());
        if (!permissions.isCanViewContract()) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }
    }

    @GetMapping("/{id}/filled/file")
    public ResponseEntity<Void> redirectToFilled(@PathVariable("id") Long id, Authentication auth) {
        ensureCanViewFilled(auth, id);

        var doc = contractDocumentRepository
                .findTopByContract_IdAndTypeOrderByVersionDesc(id, ContractDocumentType.FILLED)
                .orElseThrow(() -> new AppException(ErrorCode.CONTRACT_DOC_NOT_FOUND));

        String url = fileStorageService.generatePresignedUrl(doc.getStorageUrl(), false, null);
        return ResponseEntity.status(302).header("Location", url).build();
    }
}


