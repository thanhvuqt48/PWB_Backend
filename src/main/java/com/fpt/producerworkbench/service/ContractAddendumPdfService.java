package com.fpt.producerworkbench.service;

import com.fpt.producerworkbench.dto.request.ContractAddendumPdfFillRequest;
import org.springframework.security.core.Authentication;

public interface ContractAddendumPdfService {
    byte[] fillAddendum(Authentication auth, Long contractId, ContractAddendumPdfFillRequest req);
}