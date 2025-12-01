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
public class CompareFaceResponse {

    private CompareFaceObject object;

    @JsonProperty("server_version")
    private String serverVersion;

    private String message;
}
