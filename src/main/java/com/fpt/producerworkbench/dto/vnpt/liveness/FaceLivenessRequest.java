package com.fpt.producerworkbench.dto.vnpt.liveness;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class FaceLivenessRequest {

    private String img;

    @JsonProperty("client_session")
    private String clientSession;

    private String token;
}
