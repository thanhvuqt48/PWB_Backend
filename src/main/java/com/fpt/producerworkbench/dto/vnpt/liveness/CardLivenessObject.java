package com.fpt.producerworkbench.dto.vnpt.liveness;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.io.Serializable;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CardLivenessObject implements Serializable {

    private String liveness;

    @JsonProperty("liveness_msg")
    private String livenessMsg;

    @JsonProperty("face_swapping")
    private Boolean faceSwapping;

    @JsonProperty("fake_liveness")
    private Boolean fakeLiveness;
}
