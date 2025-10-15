package com.fpt.producerworkbench.controller;

import com.fpt.producerworkbench.dto.response.ApiResponse;
import com.fpt.producerworkbench.service.S3TestService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/s3-test")
@RequiredArgsConstructor
public class S3TestController {

    private final S3TestService s3TestService;

    // --- UPLOAD ENDPOINTS ---

    /**
     * Test upload ảnh đại diện người dùng.
     * Dùng form-data với key "userId" và "file".
     */
    @PostMapping("/upload/user-avatar")
    public ApiResponse<String> uploadAvatar(@RequestParam Long userId, @RequestParam("file") MultipartFile file) {
        String key = s3TestService.uploadUserAvatar(userId, file);
        return ApiResponse.<String>builder().result(key).message("Upload avatar thành công. Key: " + key).build();
    }

    /**
     * Test upload file nhạc cho dự án.
     * Dùng form-data với key "projectId" và "file".
     */
    @PostMapping("/upload/project-music")
    public ApiResponse<String> uploadMusic(@RequestParam Long projectId, @RequestParam("file") MultipartFile file) {
        String key = s3TestService.uploadProjectMusic(projectId, file);
        return ApiResponse.<String>builder().result(key).message("Upload nhạc thành công. Key: " + key).build();
    }

    /**
     * Test upload file video cho dự án.
     * Dùng form-data với key "projectId" và "file".
     */
    @PostMapping("/upload/project-video")
    public ApiResponse<String> uploadVideo(@RequestParam Long projectId, @RequestParam("file") MultipartFile file) {
        String key = s3TestService.uploadProjectVideo(projectId, file);
        return ApiResponse.<String>builder().result(key).message("Upload video thành công. Key: " + key).build();
    }

    /**
     * Test upload file zip cho cột mốc.
     * Dùng form-data với key "projectId", "milestoneId", và "file".
     */
    @PostMapping("/upload/milestone-zip")
    public ApiResponse<String> uploadZip(@RequestParam Long projectId, @RequestParam Long milestoneId, @RequestParam("file") MultipartFile file) {
        String key = s3TestService.uploadMilestoneZip(projectId, milestoneId, file);
        return ApiResponse.<String>builder().result(key).message("Upload file zip thành công. Key: " + key).build();
    }

    /**
     * Test upload file PDF cho hợp đồng.
     * Dùng form-data với key "contractId", "fileName" (ví dụ: "signed_final.pdf"), và "file".
     */
    @PostMapping("/upload/contract-pdf")
    public ApiResponse<String> uploadContract(@RequestParam Long contractId, @RequestParam String fileName, @RequestParam("file") MultipartFile file) {
        String key = s3TestService.uploadContractPdf(contractId, fileName, file);
        return ApiResponse.<String>builder().result(key).message("Upload hợp đồng thành công. Key: " + key).build();
    }

    /**
     * Lấy URL để XEM TRỰC TIẾP file trên trình duyệt.
     * Truyền object key bạn nhận được từ API upload vào query param "key".
     */
    @GetMapping("/view")
    public ApiResponse<String> getViewUrl(@RequestParam String key) {
        String url = s3TestService.getViewableUrl(key);
        return ApiResponse.<String>builder().result(url).message("URL để XEM TRỰC TIẾP, có hiệu lực trong 15 phút.").build();
    }

    /**
     * Lấy URL để BUỘC TẢI XUỐNG file về máy.
     * Truyền object key và tên file gốc (để gợi ý tên file khi lưu).
     */
    @GetMapping("/download")
    public ApiResponse<String> getDownloadUrl(@RequestParam String key, @RequestParam String originalFileName) {
        String url = s3TestService.getDownloadUrl(key, originalFileName);
        return ApiResponse.<String>builder().result(url).message("URL để TẢI XUỐNG, có hiệu lực trong 15 phút.").build();
    }

    /**
     * Xóa một file khỏi S3.
     */
    @DeleteMapping("/delete")
    public ApiResponse<Void> deleteFile(@RequestParam String key) {
        s3TestService.deleteFile(key);
        return ApiResponse.<Void>builder().message("Đã xóa file với key: " + key).build();
    }

    @PostMapping("/upload/project-files-multiple")
    public ApiResponse<List<String>> uploadMultipleProjectFiles(
            @RequestParam Long projectId,
            @RequestParam("files") List<MultipartFile> files) {

        List<String> keys = s3TestService.uploadProjectFiles(projectId, files);

        return ApiResponse.<List<String>>builder()
                .result(keys)
                .message(String.format("Upload thành công %d file.", keys.size()))
                .build();
    }

}