package com.fpt.producerworkbench.dto.vnpt.liveness;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class CardLivenessResponse implements Serializable {

    private String message;
    private CardLivenessObject object;
}
