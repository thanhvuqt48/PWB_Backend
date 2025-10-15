package com.fpt.producerworkbench.service;

import com.fpt.producerworkbench.dto.request.ContractCreateRequest;
import com.fpt.producerworkbench.entity.Contract;
import org.springframework.security.core.Authentication;

public interface ContractService {
    Contract createDraftContract(Authentication auth, Long projectId, ContractCreateRequest req);
}