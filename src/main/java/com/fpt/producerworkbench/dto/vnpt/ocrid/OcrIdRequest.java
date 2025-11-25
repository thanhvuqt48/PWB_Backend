package com.fpt.producerworkbench.dto.vnpt.ocrid;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class OcrIdRequest {

    @JsonProperty("img_front")
    private String imgFront;

    @JsonProperty("img_back")
    private String imgBack;

    @JsonProperty("client_session")
    private String clientSession;

    private Integer type;

    @JsonProperty("validate_postcode")
    private Boolean validatePostcode;

    private String token;
}
