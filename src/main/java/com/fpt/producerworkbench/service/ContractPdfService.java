package com.fpt.producerworkbench.service;

import com.fpt.producerworkbench.dto.request.ContractPdfFillRequest;
import org.springframework.security.core.Authentication;

public interface ContractPdfService {
    byte[] fillTemplate(Authentication auth, Long projectId, ContractPdfFillRequest req);
}
