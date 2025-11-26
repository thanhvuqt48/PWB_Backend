package com.fpt.producerworkbench.service.impl;

import com.fpt.producerworkbench.configuration.VnptProperties;
import com.fpt.producerworkbench.dto.vnpt.auth.TokenExchangeRequest;
import com.fpt.producerworkbench.dto.vnpt.auth.TokenExchangeResponse;
import com.fpt.producerworkbench.dto.vnpt.classify.ClassifyRequest;
import com.fpt.producerworkbench.dto.vnpt.classify.ClassifyResponse;
import com.fpt.producerworkbench.dto.vnpt.compare.CompareFaceRequest;
import com.fpt.producerworkbench.dto.vnpt.compare.CompareFaceResponse;
import com.fpt.producerworkbench.dto.vnpt.file.UploadFileResponse;
import com.fpt.producerworkbench.dto.vnpt.liveness.CardLivenessRequest;
import com.fpt.producerworkbench.dto.vnpt.liveness.CardLivenessResponse;
import com.fpt.producerworkbench.dto.vnpt.liveness.FaceLivenessRequest;
import com.fpt.producerworkbench.dto.vnpt.liveness.FaceLivenessResponse;
import com.fpt.producerworkbench.dto.vnpt.ocrid.OcrIdRequest;
import com.fpt.producerworkbench.dto.vnpt.ocrid.OcrIdReponse;
import com.fpt.producerworkbench.repository.http_client.VnptEkycClient;
import com.fpt.producerworkbench.service.EkycTokenService;
import com.fpt.producerworkbench.service.VnptEkycService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class VnptEkycServiceImpl implements VnptEkycService {

    private static final String TOKEN_KEY = "vnpt:access_token";
    private static final String CLIENT_ID = "8_hour";
    private static final String CLIENT_SECRET = "password";
    private static final String GRANT_TYPE = "password";
    private static final String MAC_ADDRESS = "TEST1";

    private final VnptEkycClient vnptEkycClient;
    private final VnptProperties vnptProperties;
    private final EkycTokenService ekycTokenService;

    public String getAccessToken() {
        String accessToken = ekycTokenService.getToken(TOKEN_KEY);

        if (accessToken != null && !accessToken.isBlank()) {
            return accessToken;
        }

        try {
            TokenExchangeResponse response = vnptEkycClient.authenticate(TokenExchangeRequest.builder()
                    .username(vnptProperties.getUsername())
                    .password(vnptProperties.getPassword())
                    .grantType(GRANT_TYPE)
                    .clientId(CLIENT_ID)
                    .clientSecret(CLIENT_SECRET)
                    .build());

            ekycTokenService.saveToken(response.getAccessToken(), response.getExpiresIn(), TimeUnit.HOURS);

            log.info("Token VNPT mới được lấy từ VNPT API. Expires in {} hours", response.getExpiresIn());

            return response.getAccessToken();
        } catch (Exception e) {
            log.error("Lỗi khi lấy token từ VNPT API: {}", e.getMessage(), e);
            throw new RuntimeException("Không thể lấy token từ VNPT API: " + e.getMessage(), e);
        }

    }

    public UploadFileResponse uploadFile(MultipartFile file) throws IOException {
        log.info("Uploading file to VNPT API via Feign Client");
        log.info("File name: {}, size: {} bytes", file.getOriginalFilename(), file.getSize());

        if (vnptProperties.getAccessToken() == null || vnptProperties.getAccessToken().isEmpty()) {
            log.error("VNPT accessToken is null or empty");
            throw new RuntimeException("VNPT accessToken is not configured");
        }
        if (vnptProperties.getTokenId() == null || vnptProperties.getTokenId().isEmpty()) {
            log.error("VNPT tokenId is null or empty");
            throw new RuntimeException("VNPT tokenId is not configured");
        }
        if (vnptProperties.getTokenKey() == null || vnptProperties.getTokenKey().isEmpty()) {
            log.error("VNPT tokenKey is null or empty");
            throw new RuntimeException("VNPT tokenKey is not configured");
        }

        String authorization = "Bearer " + getAccessToken();
        String macAddress = "PWB_EKYC_1.0.0";

        try {
            UploadFileResponse response = vnptEkycClient.uploadFile(
                    authorization,
                    vnptProperties.getTokenId(),
                    vnptProperties.getTokenKey(),
                    macAddress,
                    file,
                    "cccd_verification",
                    "CCCD image for eKYC");

            if (response == null) {
                log.error("VNPT API returned null response for file upload");
                throw new RuntimeException("VNPT API returned null response");
            }

            if (response.getObject() == null) {
                log.error("VNPT API response missing 'object' field: {}", response);
                throw new RuntimeException("VNPT API response format invalid: missing object");
            }

            String hash = response.getObject().getHash();
            if (hash == null || hash.trim().isEmpty()) {
                log.error("VNPT API returned null or empty hash in response: {}", response);
                throw new RuntimeException("VNPT API returned invalid hash. Cannot proceed with verification.");
            }

            log.info("File uploaded successfully, hash: {} (length: {})",
                    hash.substring(0, Math.min(20, hash.length())) + "...", hash.length());
            return response;
        } catch (feign.FeignException.Unauthorized e) {
            log.error("=== VNPT API 401 Unauthorized Error ===");
            log.error("Response status: {}", e.status());
            log.error("Response body: {}", e.contentUTF8());
            log.error("Please verify:");
            log.error("  1. VNPT_EKYC_ACCESS_TOKEN is correct and not expired");
            log.error("  2. VNPT_EKYC_TOKEN_ID is correct");
            log.error("  3. VNPT_EKYC_TOKEN_KEY is correct");
            log.error("  4. All credentials are properly set in environment variables");
            throw new RuntimeException(
                    "VNPT API authentication failed. Please check your credentials. See logs for details.", e);
        } catch (feign.FeignException e) {
            log.error("Error calling VNPT upload file API: {}", e.getMessage(), e);
            log.error("Response status: {}, body: {}", e.status(), e.contentUTF8());
            throw new RuntimeException("Failed to upload file to VNPT: " + e.getMessage(), e);
        }
    }

    public OcrIdReponse ocrCccd(String frontHash, String backHash) {
        log.info("Calling VNPT OCR API via Feign Client - frontHash: {}, backHash: {}",
                frontHash != null ? frontHash.substring(0, Math.min(10, frontHash.length())) + "..." : "null",
                backHash != null ? backHash.substring(0, Math.min(10, backHash.length())) + "..." : "null");

        if (vnptProperties.getAccessToken() == null || vnptProperties.getAccessToken().isEmpty()) {
            log.error("VNPT accessToken is null or empty");
            throw new RuntimeException("VNPT accessToken is not configured");
        }
        if (vnptProperties.getTokenId() == null || vnptProperties.getTokenId().isEmpty()) {
            log.error("VNPT tokenId is null or empty");
            throw new RuntimeException("VNPT tokenId is not configured");
        }
        if (vnptProperties.getTokenKey() == null || vnptProperties.getTokenKey().isEmpty()) {
            log.error("VNPT tokenKey is null or empty");
            throw new RuntimeException("VNPT tokenKey is not configured");
        }

        OcrIdRequest request = OcrIdRequest.builder()
                .imgFront(frontHash)
                .imgBack(backHash)
                .clientSession("PWB_EKYC_" + System.currentTimeMillis())
                .type(-1)
                .validatePostcode(true)
                .token(UUID.randomUUID().toString())
                .build();

        String authorization = "Bearer " + getAccessToken();
        String macAddress = "PWB_EKYC_1.0.0";

        try {
            OcrIdReponse response = vnptEkycClient.ocrCccd(
                    authorization,
                    vnptProperties.getTokenId(),
                    vnptProperties.getTokenKey(),
                    macAddress,
                    request);

            if (response == null) {
                log.error("VNPT OCR API returned null response");
                throw new RuntimeException("VNPT OCR API returned null response");
            }

            if (response.getMessage() != null && !response.getMessage().isEmpty()) {
                log.warn("VNPT OCR API returned message: {}", response.getMessage());
            }

            if (response.getObject() == null) {
                log.error("VNPT OCR API response missing 'object' field: {}", response);
                throw new RuntimeException("VNPT OCR API response format invalid: missing object");
            }

            log.info("OCR completed successfully");
            return response;
        } catch (feign.FeignException e) {
            log.error("Error calling VNPT OCR API: {}", e.getMessage(), e);
            log.error("Response status: {}, body: {}", e.status(), e.contentUTF8());
            throw new RuntimeException("Failed to perform OCR on CCCD: " + e.getMessage(), e);
        }
    }

    public ClassifyResponse classifyCccd(String imgCardHash) {
        log.info("Calling VNPT Classify API via Feign Client - imgCardHash: {}",
                imgCardHash != null ? imgCardHash.substring(0, Math.min(10, imgCardHash.length())) + "..." : "null");

        if (vnptProperties.getAccessToken() == null || vnptProperties.getAccessToken().isEmpty()) {
            log.error("VNPT accessToken is null or empty");
            throw new RuntimeException("VNPT accessToken is not configured");
        }
        if (vnptProperties.getTokenId() == null || vnptProperties.getTokenId().isEmpty()) {
            log.error("VNPT tokenId is null or empty");
            throw new RuntimeException("VNPT tokenId is not configured");
        }
        if (vnptProperties.getTokenKey() == null || vnptProperties.getTokenKey().isEmpty()) {
            log.error("VNPT tokenKey is null or empty");
            throw new RuntimeException("VNPT tokenKey is not configured");
        }

        ClassifyRequest request = ClassifyRequest.builder()
                .imgCard(imgCardHash)
                .clientSession("PWB_EKYC_" + System.currentTimeMillis())
                .token(UUID.randomUUID().toString())
                .build();

        String authorization = "Bearer " + getAccessToken();
        String macAddress = "PWB_EKYC_1.0.0";

        try {
            ClassifyResponse response = vnptEkycClient.classifyCccd(
                    authorization,
                    vnptProperties.getTokenId(),
                    vnptProperties.getTokenKey(),
                    macAddress,
                    request);

            if (response == null || response.getObject() == null) {
                log.error("VNPT Classify API returned null response or null object");
                throw new RuntimeException("VNPT Classify API returned null response");
            }

            log.info("Classify completed successfully - type: {}, name: {}",
                    response.getObject().getType(), response.getObject().getName());
            return response;
        } catch (feign.FeignException e) {
            log.error("Error calling VNPT Classify API: {}", e.getMessage(), e);
            log.error("Response status: {}, body: {}", e.status(), e.contentUTF8());
            throw new RuntimeException("Failed to classify CCCD: " + e.getMessage(), e);
        }
    }

    public CompareFaceResponse compareFace(String imgFrontHash, String imgFaceHash) {
        log.info("Calling VNPT Compare Face API via Feign Client - imgFrontHash: {}, imgFaceHash: {}",
                imgFrontHash != null ? imgFrontHash.substring(0, Math.min(10, imgFrontHash.length())) + "..." : "null",
                imgFaceHash != null ? imgFaceHash.substring(0, Math.min(10, imgFaceHash.length())) + "..." : "null");

        if (vnptProperties.getAccessToken() == null || vnptProperties.getAccessToken().isEmpty()) {
            log.error("VNPT accessToken is null or empty");
            throw new RuntimeException("VNPT accessToken is not configured");
        }
        if (vnptProperties.getTokenId() == null || vnptProperties.getTokenId().isEmpty()) {
            log.error("VNPT tokenId is null or empty");
            throw new RuntimeException("VNPT tokenId is not configured");
        }
        if (vnptProperties.getTokenKey() == null || vnptProperties.getTokenKey().isEmpty()) {
            log.error("VNPT tokenKey is null or empty");
            throw new RuntimeException("VNPT tokenKey is not configured");
        }

        CompareFaceRequest request = CompareFaceRequest.builder()
                .imgFront(imgFrontHash)
                .imgFace(imgFaceHash)
                .clientSession("PWB_EKYC_" + System.currentTimeMillis())
                .token(UUID.randomUUID().toString())
                .build();

        String authorization = "Bearer " + getAccessToken();
        String macAddress = "PWB_EKYC_1.0.0";

        try {
            CompareFaceResponse response = vnptEkycClient.compareFace(
                    authorization,
                    vnptProperties.getTokenId(),
                    vnptProperties.getTokenKey(),
                    macAddress,
                    request);

            if (response == null || response.getObject() == null) {
                log.error("VNPT Compare Face API returned null response or null object");
                throw new RuntimeException("VNPT Compare Face API returned null response");
            }

            log.info("Compare Face completed successfully - result: {}, msg: {}",
                    response.getObject().getResult(), response.getObject().getMsg());
            return response;
        } catch (feign.FeignException.Unauthorized e) {
            log.error("=== VNPT Compare Face API 401 Unauthorized Error ===");
            log.error("Response status: {}", e.status());
            log.error("Response body: {}", e.contentUTF8());
            log.error("Please verify:");
            log.error("  1. VNPT_EKYC_ACCESS_TOKEN is correct and not expired");
            log.error("  2. VNPT_EKYC_TOKEN_ID is correct");
            log.error("  3. VNPT_EKYC_TOKEN_KEY is correct");
            log.error("  4. All credentials are properly set in environment variables");
            log.error("  5. Access token may need to be refreshed for this specific endpoint");
            throw new RuntimeException(
                    "VNPT Compare Face API authentication failed. Please check your credentials. See logs for details.",
                    e);
        } catch (feign.FeignException e) {
            log.error("Error calling VNPT Compare Face API: {}", e.getMessage(), e);
            log.error("Response status: {}, body: {}", e.status(), e.contentUTF8());
            throw new RuntimeException("Failed to compare face: " + e.getMessage(), e);
        }
    }

    public CardLivenessResponse liveness(String imgCardHash) {
        log.info("Calling VNPT Liveness API via Feign Client - imgCardHash: {}",
                imgCardHash != null ? imgCardHash.substring(0, Math.min(10, imgCardHash.length())) + "..." : "null");

        // Validate imgCardHash
        if (imgCardHash == null || imgCardHash.trim().isEmpty()) {
            log.error("VNPT Liveness API: imgCardHash is null or empty");
            throw new RuntimeException("Image card hash is required for liveness check");
        }

        if (vnptProperties.getAccessToken() == null || vnptProperties.getAccessToken().isEmpty()) {
            log.error("VNPT accessToken is null or empty");
            throw new RuntimeException("VNPT accessToken is not configured");
        }
        if (vnptProperties.getTokenId() == null || vnptProperties.getTokenId().isEmpty()) {
            log.error("VNPT tokenId is null or empty");
            throw new RuntimeException("VNPT tokenId is not configured");
        }
        if (vnptProperties.getTokenKey() == null || vnptProperties.getTokenKey().isEmpty()) {
            log.error("VNPT tokenKey is null or empty");
            throw new RuntimeException("VNPT tokenKey is not configured");
        }

        CardLivenessRequest request = CardLivenessRequest.builder()
                .img(imgCardHash)
                .clientSession("PWB_EKYC_" + System.currentTimeMillis())
                .build();

        log.debug("VNPT Liveness Request - img: {}, clientSession: {}",
                imgCardHash.substring(0, Math.min(20, imgCardHash.length())) + "...",
                request.getClientSession());

        String authorization = "Bearer " + getAccessToken();
        String macAddress = "PWB_EKYC_1.0.0";

        try {
            CardLivenessResponse response = vnptEkycClient.liveness(
                    authorization,
                    vnptProperties.getTokenId(),
                    vnptProperties.getTokenKey(),
                    macAddress,
                    request);

            if (response == null || response.getObject() == null) {
                log.error("VNPT Liveness API returned null response or null object");
                throw new RuntimeException("VNPT Liveness API returned null response");
            }

            log.info("Liveness completed successfully - liveness: {}, fakeLiveness: {}",
                    response.getObject().getLiveness(), response.getObject().getFakeLiveness());
            return response;
        } catch (feign.FeignException.BadRequest e) {
            log.error("=== VNPT Liveness API 400 Bad Request Error ===");
            log.error("Response status: {}", e.status());
            String responseBody = e.contentUTF8();
            log.error("Response body: {}", responseBody);
            log.error("Request imgCardHash: {}",
                    imgCardHash != null ? imgCardHash.substring(0, Math.min(20, imgCardHash.length())) + "..."
                            : "null");
            log.error("Possible causes:");
            log.error("  1. Image hash (imgCard) is invalid or expired");
            log.error("  2. Image hash format is incorrect");
            log.error("  3. Image was not uploaded successfully to VNPT");
            log.error("  4. Request format does not match VNPT API requirements");

            // Try to extract error message from response
            String errorMessage = "VNPT Liveness API validation failed";
            if (responseBody != null && responseBody.contains("IDG-")) {
                errorMessage += ". Error code: IDG-00000001 (Invalid image hash)";
            }

            throw new RuntimeException(errorMessage + ". See logs for details.", e);
        } catch (feign.FeignException.Unauthorized e) {
            log.error("=== VNPT Liveness API 401 Unauthorized Error ===");
            log.error("Response status: {}", e.status());
            log.error("Response body: {}", e.contentUTF8());
            log.error("Please verify VNPT credentials are correct and not expired");
            throw new RuntimeException(
                    "VNPT Liveness API authentication failed. Please check your credentials.", e);
        } catch (feign.FeignException e) {
            log.error("Error calling VNPT Liveness API: {}", e.getMessage(), e);
            log.error("Response status: {}, body: {}", e.status(), e.contentUTF8());
            throw new RuntimeException("Failed to check liveness: " + e.getMessage(), e);
        }
    }

    public FaceLivenessResponse faceLiveness(String imgHash) {
        log.info("Calling VNPT Face Liveness API via Feign Client - imgHash: {}",
                imgHash != null ? imgHash.substring(0, Math.min(10, imgHash.length())) + "..." : "null");

        if (vnptProperties.getAccessToken() == null || vnptProperties.getAccessToken().isEmpty()) {
            log.error("VNPT accessToken is null or empty");
            throw new RuntimeException("VNPT accessToken is not configured");
        }
        if (vnptProperties.getTokenId() == null || vnptProperties.getTokenId().isEmpty()) {
            log.error("VNPT tokenId is null or empty");
            throw new RuntimeException("VNPT tokenId is not configured");
        }
        if (vnptProperties.getTokenKey() == null || vnptProperties.getTokenKey().isEmpty()) {
            log.error("VNPT tokenKey is null or empty");
            throw new RuntimeException("VNPT tokenKey is not configured");
        }

        FaceLivenessRequest request = FaceLivenessRequest.builder()
                .img(imgHash)
                .clientSession("PWB_EKYC_" + System.currentTimeMillis())
                .token(UUID.randomUUID().toString())
                .build();

        String authorization = "Bearer " + getAccessToken();
        String macAddress = "PWB_EKYC_1.0.0";

        try {
            FaceLivenessResponse response = vnptEkycClient.faceLiveness(
                    authorization,
                    vnptProperties.getTokenId(),
                    vnptProperties.getTokenKey(),
                    macAddress,
                    request);

            if (response == null || response.getObject() == null) {
                log.error("VNPT Face Liveness API returned null response or null object");
                throw new RuntimeException("VNPT Face Liveness API returned null response");
            }

            log.info("Face Liveness completed successfully - liveness: {}, isEyeOpen: {}",
                    response.getObject().getLiveness(), response.getObject().getIsEyeOpen());
            return response;
        } catch (feign.FeignException e) {
            log.error("Error calling VNPT Face Liveness API: {}", e.getMessage(), e);
            log.error("Response status: {}, body: {}", e.status(), e.contentUTF8());
            throw new RuntimeException("Failed to check face liveness: " + e.getMessage(), e);
        }
    }
}
