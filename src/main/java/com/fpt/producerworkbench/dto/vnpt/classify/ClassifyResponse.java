package com.fpt.producerworkbench.dto.vnpt.classify;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ClassifyResponse implements Serializable {

    private String message;
    private ClassifyObject object;
}
