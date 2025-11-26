package com.fpt.producerworkbench.dto.vnpt.liveness;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class FaceLivenessObject {

    private String liveness;

    @JsonProperty("liveness_msg")
    private String livenessMsg;

    @JsonProperty("is_eye_open")
    private String isEyeOpen;
}
