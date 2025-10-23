package com.fpt.producerworkbench.dto.response;

import com.fpt.producerworkbench.dto.request.ContractInviteRequest;
import lombok.*;

@Setter
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StartSigningResponse {
    private String inviteId;
}
