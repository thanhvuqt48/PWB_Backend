package com.fpt.producerworkbench.dto.vietqr;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VietQrGenerateRequest {
    private String accountNo;
    private String accountName;
    private String acqId;
    private Long amount;
    private String addInfo;
    private String format;
    private String template;
}

