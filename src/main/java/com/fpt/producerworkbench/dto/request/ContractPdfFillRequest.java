package com.fpt.producerworkbench.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

@Getter @Setter @NoArgsConstructor
public class ContractPdfFillRequest extends ContractDataBaseRequest {

    @JsonProperty("contractNo")
    private String contractNo;

    @JsonProperty("signPlace")
    private String signPlace;
}