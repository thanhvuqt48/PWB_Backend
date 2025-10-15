package com.fpt.producerworkbench.service;

import com.fpt.producerworkbench.dto.response.SignedContractResponse;

public interface ContractSigningService {
    SignedContractResponse saveSignedAndComplete(Long contractId, boolean withHistory);
}

