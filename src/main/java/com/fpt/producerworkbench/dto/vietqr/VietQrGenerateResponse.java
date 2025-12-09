package com.fpt.producerworkbench.dto.vietqr;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VietQrGenerateResponse {
    private String code;
    private String desc;
    private VietQrData data;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VietQrData {
        private String qrCode;
        private String qrDataURL;
    }
}

