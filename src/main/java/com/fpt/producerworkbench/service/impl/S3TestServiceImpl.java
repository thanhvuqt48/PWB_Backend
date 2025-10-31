package com.fpt.producerworkbench.service.impl;

import com.fpt.producerworkbench.service.FileKeyGenerator;
import com.fpt.producerworkbench.service.FileStorageService;
import com.fpt.producerworkbench.service.S3TestService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class S3TestServiceImpl implements S3TestService {

    private final FileKeyGenerator fileKeyGenerator;
    private final FileStorageService fileStorageService;

    @Override
    public String uploadUserAvatar(Long userId, MultipartFile file) {
        String key = fileKeyGenerator.generateUserAvatarKey(userId, file.getOriginalFilename());
        fileStorageService.uploadFile(file, key);
        return key;
    }

    @Override
    public String uploadProjectMusic(Long projectId, MultipartFile file) {
        String key = fileKeyGenerator.generateProjectFileKey(projectId, file.getOriginalFilename());
        fileStorageService.uploadFile(file, key);
        return key;
    }

    @Override
    public String uploadProjectVideo(Long projectId, MultipartFile file) {
        String key = fileKeyGenerator.generateProjectFileKey(projectId, file.getOriginalFilename());
        fileStorageService.uploadFile(file, key);
        return key;
    }

    @Override
    public String uploadProjectImage(Long projectId, MultipartFile file) {
        String key = fileKeyGenerator.generateProjectFileKey(projectId, file.getOriginalFilename());
        fileStorageService.uploadFile(file, key);
        return key;
    }

    @Override
    public String uploadMilestoneZip(Long projectId, Long milestoneId, MultipartFile file) {
        String key = fileKeyGenerator.generateMilestoneDeliveryKey(projectId, milestoneId, file.getOriginalFilename());
        fileStorageService.uploadFile(file, key);
        return key;
    }

    @Override
    public String uploadContractPdf(Long contractId, String fileName, MultipartFile file) {
        String key = fileKeyGenerator.generateContractDocumentKey(contractId, fileName);
        fileStorageService.uploadFile(file, key);
        return key;
    }

    @Override
    public String uploadPortfolioCoverImage(Long userId, MultipartFile file) {
        String key = fileKeyGenerator.generatePortfolioCoverImageKey(userId, file.getOriginalFilename());
        fileStorageService.uploadFile(file, key);
        return key;
    }

    @Override
    public String uploadPersonalProjectImage(Long userId, Long personalProjectId, MultipartFile file) {
        String key = fileKeyGenerator.generatePersonalProjectImageKey(userId, personalProjectId, file.getOriginalFilename());
        fileStorageService.uploadFile(file, key);
        return key;
    }

    @Override
    public String getViewableUrl(String objectKey) {
        return fileStorageService.generatePresignedUrl(objectKey, false, null);
    }

    @Override
    public String getDownloadUrl(String objectKey, String originalFileName) {
        // Gọi service với forDownload = true và cung cấp tên file
        return fileStorageService.generatePresignedUrl(objectKey, true, originalFileName);
    }

    @Override
    public void deleteFile(String objectKey) {
        fileStorageService.deleteFile(objectKey);
    }

    @Override
    public List<String> uploadProjectFiles(Long projectId, List<MultipartFile> files) {
        List<CompletableFuture<String>> futures = files.stream().map(file ->
                CompletableFuture.supplyAsync(() -> {
                    String key = fileKeyGenerator.generateProjectFileKey(projectId, file.getOriginalFilename());
                    fileStorageService.uploadFile(file, key);
                    return key;
                })
        ).collect(Collectors.toList());

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        return futures.stream()
                .map(CompletableFuture::join)
                .collect(Collectors.toList());
    }
}