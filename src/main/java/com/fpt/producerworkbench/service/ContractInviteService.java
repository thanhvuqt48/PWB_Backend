package com.fpt.producerworkbench.service;

import com.fpt.producerworkbench.dto.request.ContractInviteRequest;
import com.fpt.producerworkbench.dto.response.StartSigningResponse;
import org.springframework.security.core.Authentication;

public interface ContractInviteService {
    StartSigningResponse invite(Authentication auth, Long contractId, ContractInviteRequest req);
}
