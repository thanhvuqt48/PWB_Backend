package com.fpt.producerworkbench.dto.vnpt.compare;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.io.Serializable;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CompareFaceRequest implements Serializable {

    @JsonProperty("img_front")
    private String imgFront;

    @JsonProperty("img_face")
    private String imgFace;

    @JsonProperty("client_session")
    private String clientSession;
    private String token;
}
