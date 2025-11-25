package com.fpt.producerworkbench.dto.vnpt.compare;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class CompareFaceObject {

    private String result;
    private String msg;

    @JsonProperty("multiple_faces")
    private Boolean multipleFaces;
}
