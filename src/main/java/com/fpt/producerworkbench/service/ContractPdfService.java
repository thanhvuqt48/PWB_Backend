package com.fpt.producerworkbench.service;

import com.fpt.producerworkbench.dto.request.ContractPdfFillRequest;
import org.springframework.security.core.Authentication;

public interface ContractPdfService {

    byte[] generateAndSendReviewPdf(Authentication auth, Long contractId, ContractPdfFillRequest req);
}
