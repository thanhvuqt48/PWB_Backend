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
public class ClassifyObject implements Serializable {

    private Integer type;
    private String name;
}
