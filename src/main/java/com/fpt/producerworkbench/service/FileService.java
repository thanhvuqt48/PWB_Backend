package com.fpt.producerworkbench.service;

import com.fpt.producerworkbench.dto.response.FileMetaDataResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface FileService {

    String uploadUserAvatar(Long userId, MultipartFile file);
    String uploadProjectMusic(Long projectId, MultipartFile file);
    String uploadProjectVideo(Long projectId, MultipartFile file);
    String uploadProjectImage(Long projectId, MultipartFile file);
    String uploadMilestoneZip(Long projectId, Long milestoneId, MultipartFile file);
    String uploadContractPdf(Long contractId, String fileName, MultipartFile file);
    String uploadPortfolioCoverImage(Long userId, MultipartFile file);
    String uploadPersonalProjectImage(Long userId, Long personalProjectId, MultipartFile file);

    List<String> uploadProjectFiles(Long projectId, List<MultipartFile> files);

    String getViewableUrl(String objectKey);

    String getDownloadUrl(String objectKey, String originalFileName);
    void deleteFile(String objectKey);

    CompletableFuture<List<FileMetaDataResponse>> uploadChatMessageFile(String conversationId, List<MultipartFile> files);
}
