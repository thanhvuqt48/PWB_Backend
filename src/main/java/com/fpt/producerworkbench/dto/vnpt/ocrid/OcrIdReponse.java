package com.fpt.producerworkbench.dto.vnpt.ocrid;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class OcrIdReponse {

    private OcrIdObject object;

    private String message;

    @JsonProperty("server_version")
    private String serverVersion;
}
