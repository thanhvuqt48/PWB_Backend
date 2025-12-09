package com.fpt.producerworkbench.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BankResponse {
    private Long id;
    private String code;
    private String name;
    private String shortName;
    private String bin;
    private String logoUrl;
    private Boolean transferSupported;
    private Boolean lookupSupported;
    private String swiftCode;
}

