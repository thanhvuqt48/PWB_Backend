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
public class FaceLivenessObject {

    private String liveness;

    @JsonProperty("liveness_msg")
    private String livenessMsg;

    @JsonProperty("is_eye_open")
    private String isEyeOpen;
}
