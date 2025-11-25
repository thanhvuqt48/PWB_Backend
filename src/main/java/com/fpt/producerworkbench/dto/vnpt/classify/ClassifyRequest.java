package com.fpt.producerworkbench.dto.vnpt.classify;

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
public class ClassifyRequest implements Serializable {

    @JsonProperty("img_card")
    private String imgCard;

    @JsonProperty("client_session")
    private String clientSession;

    private String token;
}
