package com.fpt.producerworkbench.controller;

import com.fpt.producerworkbench.dto.response.ApiResponse;
import com.fpt.producerworkbench.dto.response.UploadResponse;
import com.fpt.producerworkbench.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/test/s3")
@RequiredArgsConstructor
public class S3TestController {

    private final FileStorageService fileStorageService;

    @Value("${cloudfront.domain}")
    private String cloudfrontDomain;

    /**
     * API để test chức năng upload file.
     * Dùng Postman (hoặc công cụ tương tự) với phương thức POST, chọn Body -> form-data.
     * Tạo một key tên là "file" và chọn type là "File", sau đó chọn file bạn muốn tải lên.
     */
    @PostMapping("/upload")
    public ResponseEntity<ApiResponse<UploadResponse>> uploadTestFile(@RequestParam("file") MultipartFile file) {

        String fileExtension = getFileExtension(file.getOriginalFilename());

        String objectKey = "test-uploads/" + UUID.randomUUID().toString() + fileExtension;

        fileStorageService.uploadFile(file, objectKey);

        String publicUrl = "https://" + cloudfrontDomain + "/" + objectKey;

        UploadResponse result = UploadResponse.builder()
                .objectKey(objectKey)
                .url(publicUrl)
                .build();

        return ResponseEntity.ok(ApiResponse.<UploadResponse>builder()
                .message("Test file uploaded successfully.")
                .result(result)
                .build());
    }

    @GetMapping("/view")
    public ResponseEntity<ApiResponse<String>> getTestFileUrl(@RequestParam("key") String objectKey) {
        String publicUrl = "https://" + cloudfrontDomain + "/" + objectKey;

        return ResponseEntity.ok(ApiResponse.<String>builder()
                .message("File URL retrieved.")
                .result(publicUrl)
                .build());
    }

    /**
     * API để test chức năng xóa file.
     * Dùng phương thức DELETE và truyền objectKey qua query parameter "key".
     * Ví dụ: DELETE http://localhost:8080/api/v1/test/s3/delete?key=test-uploads/your-file-key.mp3
     */
    // SỬA LẠI MAPPING VÀ THAM SỐ Ở ĐÂY
    @DeleteMapping("/delete")
    public ResponseEntity<ApiResponse<String>> deleteTestFile(@RequestParam("key") String objectKey) {
        fileStorageService.deleteFile(objectKey);

        return ResponseEntity.ok(ApiResponse.<String>builder()
                .message("File '" + objectKey + "' has been deleted.")
                .build());
    }

    /**
     * Phương thức private để lấy đuôi file một cách an toàn.
     */
    private String getFileExtension(String filename) {
        if (!StringUtils.hasText(filename) || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf("."));
    }
}