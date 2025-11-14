package com.fpt.producerworkbench.controller;

import com.fpt.producerworkbench.dto.response.ApiResponse;
import com.fpt.producerworkbench.service.FileService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RequestMapping("/api/v1/files")
@Slf4j
public class FileController {

    private final FileService fileService;

    @PostMapping("/upload/user-avatar")
    public ApiResponse<String> uploadAvatar(@RequestParam Long userId, @RequestParam("file") MultipartFile file) {
        String key = fileService.uploadUserAvatar(userId, file);
        return ApiResponse.<String>builder().result(key).message("Upload avatar thành công. Key: " + key).build();
    }

    @PostMapping("/upload/project-music")
    public ApiResponse<String> uploadMusic(@RequestParam Long projectId, @RequestParam("file") MultipartFile file) {
        String key = fileService.uploadProjectMusic(projectId, file);
        return ApiResponse.<String>builder().result(key).message("Upload nhạc thành công. Key: " + key).build();
    }

    @PostMapping("/upload/project-video")
    public ApiResponse<String> uploadVideo(@RequestParam Long projectId, @RequestParam("file") MultipartFile file) {
        String key = fileService.uploadProjectVideo(projectId, file);
        return ApiResponse.<String>builder().result(key).message("Upload video thành công. Key: " + key).build();
    }

    @PostMapping("/upload/milestone-zip")
    public ApiResponse<String> uploadZip(@RequestParam Long projectId, @RequestParam Long milestoneId, @RequestParam("file") MultipartFile file) {
        String key = fileService.uploadMilestoneZip(projectId, milestoneId, file);
        return ApiResponse.<String>builder().result(key).message("Upload file zip thành công. Key: " + key).build();
    }

    @PostMapping("/upload/contract-pdf")
    public ApiResponse<String> uploadContract(@RequestParam Long contractId, @RequestParam String fileName, @RequestParam("file") MultipartFile file) {
        String key = fileService.uploadContractPdf(contractId, fileName, file);
        return ApiResponse.<String>builder().result(key).message("Upload hợp đồng thành công. Key: " + key).build();
    }

    @PostMapping("/upload/portfolio-cover")
    public ApiResponse<String> uploadPortfolioCover(@RequestParam Long userId, @RequestParam("file") MultipartFile file) {
        String key = fileService.uploadPortfolioCoverImage(userId, file);
        return ApiResponse.<String>builder().result(key).message("Upload ảnh bìa portfolio thành công. Key: " + key).build();
    }

    @PostMapping("/upload/personal-project-image")
    public ApiResponse<String> uploadPersonalProjectImage(
            @RequestParam Long userId,
            @RequestParam Long personalProjectId,
            @RequestParam("file") MultipartFile file) {
        String key = fileService.uploadPersonalProjectImage(userId, personalProjectId, file);
        return ApiResponse.<String>builder().result(key).message("Upload ảnh personal project thành công. Key: " + key).build();
    }

    @GetMapping("/view")
    public ApiResponse<String> getViewUrl(@RequestParam String key) {
        String url = fileService.getViewableUrl(key);
        return ApiResponse.<String>builder().result(url).message("URL để XEM TRỰC TIẾP, có hiệu lực trong 15 phút.").build();
    }

    @GetMapping("/download")
    public ApiResponse<String> getDownloadUrl(@RequestParam String key, @RequestParam String originalFileName) {
        String url = fileService.getDownloadUrl(key, originalFileName);
        return ApiResponse.<String>builder().result(url).message("URL để TẢI XUỐNG, có hiệu lực trong 15 phút.").build();
    }

    @DeleteMapping("/delete")
    public ApiResponse<Void> deleteFile(@RequestParam String key) {
        fileService.deleteFile(key);
        return ApiResponse.<Void>builder().message("Đã xóa file với key: " + key).build();
    }

    @PostMapping("/upload/project-files-multiple")
    public ApiResponse<List<String>> uploadMultipleProjectFiles(
            @RequestParam Long projectId,
            @RequestParam("files") List<MultipartFile> files) {

        List<String> keys = fileService.uploadProjectFiles(projectId, files);

        return ApiResponse.<List<String>>builder()
                .result(keys)
                .message(String.format("Upload thành công %d file.", keys.size()))
                .build();
    }

    @PostMapping("/upload/chat-message-files")
    public ApiResponse<List<String>> uploadChatMessageFiles(
            @RequestParam String conversationId,
            @RequestParam("files") List<MultipartFile> files) {
        List<String> keys = fileService.uploadChatMessageFile(conversationId, files);
        return ApiResponse.<List<String>>builder()
                .result(keys)
                .message(String.format("Upload thành công %d file.", keys.size()))
                .build();
    }
}
