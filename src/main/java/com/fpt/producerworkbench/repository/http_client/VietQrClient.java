package com.fpt.producerworkbench.repository.http_client;

import com.fpt.producerworkbench.dto.vietqr.VietQrGenerateRequest;
import com.fpt.producerworkbench.dto.vietqr.VietQrGenerateResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "vietqr", url = "https://api.vietqr.io")
public interface VietQrClient {

    @PostMapping(value = "/v2/generate", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    VietQrGenerateResponse generateQrCode(
            @RequestBody VietQrGenerateRequest request,
            @RequestHeader("x-api-key") String apiKey,
            @RequestHeader("x-client-id") String clientId);
}

