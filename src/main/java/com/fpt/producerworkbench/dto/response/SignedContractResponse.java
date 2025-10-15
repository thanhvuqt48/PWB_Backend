package com.fpt.producerworkbench.dto.response;

import lombok.*;

@Setter
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SignedContractResponse {
    private String storageUrl;
    private Integer version;
    private Integer size;
}
