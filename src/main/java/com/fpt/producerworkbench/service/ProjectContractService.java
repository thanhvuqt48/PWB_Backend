package com.fpt.producerworkbench.service;

import com.fpt.producerworkbench.dto.request.ContractInviteRequest;
import com.fpt.producerworkbench.dto.request.ContractPdfFillRequest;
import com.fpt.producerworkbench.dto.response.PartyBInfoResponse;
import com.fpt.producerworkbench.dto.response.StartSigningResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import java.util.Map;

public interface ProjectContractService {
    Map<String, Object> getContractByProject(Long projectId);
    ResponseEntity<Void> redirectToFilled(Long id, Authentication auth);
    StartSigningResponse invite(Authentication auth, Long contractId, ContractInviteRequest req);
    byte[] fillContractPdf(Authentication auth, Long projectId, ContractPdfFillRequest req);
    String decline(Authentication auth, Long id, String reason) throws Exception;
    String getDeclineReason(Authentication auth, Long id);
    ResponseEntity<Void> viewSignedPdf(Long id);
    Map<String, Object> syncContractStatus(Long projectId);
    PartyBInfoResponse getVerifiedPartyBInfo(Authentication auth);
}

