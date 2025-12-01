package com.fpt.producerworkbench.dto.vnpt.liveness;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class FaceLivenessResponse {

    private String message;
    private FaceLivenessObject object;
}
