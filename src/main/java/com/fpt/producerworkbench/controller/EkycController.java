package com.fpt.producerworkbench.controller;

import com.fpt.producerworkbench.dto.response.ApiResponse;
import com.fpt.producerworkbench.dto.response.CccdInfoResponse;
import com.fpt.producerworkbench.service.UserService;
import com.fpt.producerworkbench.service.VnptEkycService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/v1/ekyc")
@RequiredArgsConstructor
@Slf4j
public class EkycController {

    private final UserService userService;
    private final VnptEkycService vnptEkycService;

    @PostMapping(value = "/verify-cccd", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<CccdInfoResponse> verifyCccd(
            @RequestPart("front") MultipartFile front,
            @RequestPart("back") MultipartFile back,
            @RequestPart(value = "face", required = false) MultipartFile face
    ) throws IOException {
        log.info("Received verify-cccd request - front: {} ({} bytes), back: {} ({} bytes), face: {}",
                front != null ? front.getOriginalFilename() : "null",
                front != null ? front.getSize() : 0,
                back != null ? back.getOriginalFilename() : "null",
                back != null ? back.getSize() : 0,
                face != null ? face.getOriginalFilename() : "null");
        
        if (front == null || front.isEmpty()) {
            log.warn("Front file is null or empty");
            return ApiResponse.<CccdInfoResponse>builder()
                    .code(HttpStatus.BAD_REQUEST.value())
                    .message("File mặt trước CCCD không được để trống")
                    .build();
        }
        
        if (back == null || back.isEmpty()) {
            log.warn("Back file is null or empty");
            return ApiResponse.<CccdInfoResponse>builder()
                    .code(HttpStatus.BAD_REQUEST.value())
                    .message("File mặt sau CCCD không được để trống")
                    .build();
        }

        try {
            log.info("Starting CCCD verification process...");
            CccdInfoResponse result = userService.verifyCccd(front, back, face);
            log.info("CCCD verification completed successfully");

            return ApiResponse.<CccdInfoResponse>builder()
                    .code(HttpStatus.OK.value())
                    .message("Xác thực CCCD thành công!")
                    .result(result)
                    .build();
        } catch (Exception e) {
            log.error("Error during CCCD verification: ", e);
            throw e; // Re-throw để GlobalExceptionHandler xử lý
        }
    }

    @GetMapping("/get-token")
    public ApiResponse<String> getToken() {
        String accessToken = vnptEkycService.getAccessToken();
        return ApiResponse.<String>builder()
                .code(HttpStatus.OK.value())
                .message("Lấy token thành công!")
                .result(accessToken)
                .build();
    }

}
