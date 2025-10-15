package com.fpt.producerworkbench.service;

import com.fpt.producerworkbench.dto.request.ContractChangeRequest;

public interface ContractReviewService {
    byte[] getReviewPdf(String token);
    void approveContract(String token);
    void requestChanges(String token, ContractChangeRequest req);
}