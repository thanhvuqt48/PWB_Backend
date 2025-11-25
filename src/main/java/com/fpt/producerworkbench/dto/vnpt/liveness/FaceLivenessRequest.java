package com.fpt.producerworkbench.dto.vnpt.liveness;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class FaceLivenessRequest {

    private String img;

    @JsonProperty("client_session")
    private String clientSession;

    private String token;
}
