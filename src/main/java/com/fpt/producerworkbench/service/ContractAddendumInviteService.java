package com.fpt.producerworkbench.service;

import com.fpt.producerworkbench.dto.request.ContractInviteRequest;
import com.fpt.producerworkbench.dto.response.StartSigningResponse;
import org.springframework.security.core.Authentication;

public interface ContractAddendumInviteService {
    StartSigningResponse inviteAddendum(Authentication auth, Long contractId, ContractInviteRequest req);
}