package com.fpt.producerworkbench.repository.http_client;

import com.fpt.producerworkbench.dto.vnpt.auth.TokenExchangeRequest;
import com.fpt.producerworkbench.dto.vnpt.auth.TokenExchangeResponse;
import com.fpt.producerworkbench.dto.vnpt.classify.ClassifyRequest;
import com.fpt.producerworkbench.dto.vnpt.classify.ClassifyResponse;
import com.fpt.producerworkbench.dto.vnpt.compare.CompareFaceRequest;
import com.fpt.producerworkbench.dto.vnpt.compare.CompareFaceResponse;
import com.fpt.producerworkbench.dto.vnpt.liveness.CardLivenessRequest;
import com.fpt.producerworkbench.dto.vnpt.liveness.CardLivenessResponse;
import com.fpt.producerworkbench.dto.vnpt.liveness.FaceLivenessRequest;
import com.fpt.producerworkbench.dto.vnpt.liveness.FaceLivenessResponse;
import com.fpt.producerworkbench.dto.vnpt.ocrid.OcrIdRequest;
import com.fpt.producerworkbench.dto.vnpt.ocrid.OcrIdReponse;
import com.fpt.producerworkbench.dto.vnpt.file.UploadFileResponse;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@FeignClient(name = "vnpt-ekyc", url = "${vnpt.ekyc.base-url}", configuration = VnptEkycClientConfig.class)
public interface VnptEkycClient {

    @PostMapping(value = "/auth/oauth/token", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    TokenExchangeResponse authenticate(@RequestBody TokenExchangeRequest request);

    @PostMapping(value = "/file-service/v1/addFile", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    UploadFileResponse uploadFile(
            @RequestHeader("Authorization") String authorization,
            @RequestHeader("Token-id") String tokenId,
            @RequestHeader("Token-key") String tokenKey,
            @RequestHeader("mac-address") String macAddress,
            @RequestPart("file") MultipartFile file,
            @RequestPart("title") String title,
            @RequestPart("description") String description);

    @PostMapping(value = "/ai/v1/ocr/id", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    OcrIdReponse ocrCccd(
            @RequestHeader("Authorization") String authorization,
            @RequestHeader("Token-id") String tokenId,
            @RequestHeader("Token-key") String tokenKey,
            @RequestHeader("mac-address") String macAddress,
            @RequestBody OcrIdRequest request);

    @PostMapping(value = "/ai/v1/classify/id", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    ClassifyResponse classifyCccd(
            @RequestHeader("Authorization") String authorization,
            @RequestHeader("Token-id") String tokenId,
            @RequestHeader("Token-key") String tokenKey,
            @RequestHeader("mac-address") String macAddress,
            @RequestBody ClassifyRequest request);

    @PostMapping(value = "/ai/v1/face/compare", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    CompareFaceResponse compareFace(
            @RequestHeader("Authorization") String authorization,
            @RequestHeader("Token-id") String tokenId,
            @RequestHeader("Token-key") String tokenKey,
            @RequestHeader("mac-address") String macAddress,
            @RequestBody CompareFaceRequest request);

    @PostMapping(value = "/ai/v1/card/liveness", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    CardLivenessResponse liveness(
            @RequestHeader("Authorization") String authorization,
            @RequestHeader("Token-id") String tokenId,
            @RequestHeader("Token-key") String tokenKey,
            @RequestHeader("mac-address") String macAddress,
            @RequestBody CardLivenessRequest request);

    @PostMapping(value = "/ai/v1/face/liveness", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    FaceLivenessResponse faceLiveness(
            @RequestHeader("Authorization") String authorization,
            @RequestHeader("Token-id") String tokenId,
            @RequestHeader("Token-key") String tokenKey,
            @RequestHeader("mac-address") String macAddress,
            @RequestBody FaceLivenessRequest request);
}
