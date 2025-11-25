package com.fpt.producerworkbench.dto.vnpt.liveness;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class CardLivenessRequest implements Serializable {

    private String img;

    @JsonProperty("client_session")
    private String clientSession;

}
