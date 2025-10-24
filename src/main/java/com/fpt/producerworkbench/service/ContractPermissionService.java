package com.fpt.producerworkbench.service;

import com.fpt.producerworkbench.dto.response.ContractPermissionResponse;
import org.springframework.security.core.Authentication;

public interface ContractPermissionService {
    ContractPermissionResponse checkContractPermissions(Authentication auth, Long projectId);
}
