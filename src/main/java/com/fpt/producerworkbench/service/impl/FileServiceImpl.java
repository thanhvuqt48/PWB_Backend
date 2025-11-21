package com.fpt.producerworkbench.service.impl;

import com.fpt.producerworkbench.dto.response.FileMetaDataResponse;
import com.fpt.producerworkbench.service.FileKeyGenerator;
import com.fpt.producerworkbench.service.FileService;
import com.fpt.producerworkbench.service.FileStorageService;
import com.fpt.producerworkbench.service.PublicUrlService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FileServiceImpl implements FileService {

    private final FileKeyGenerator fileKeyGenerator;
    private final FileStorageService fileStorageService;
    private final PublicUrlService publicUrlService;

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
    public CompletableFuture<List<FileMetaDataResponse>> uploadChatMessageFile(String conversationId, List<MultipartFile> files) {
        AtomicInteger orderCounter = new AtomicInteger(0);

        List<CompletableFuture<FileMetaDataResponse>> futures = files.stream()
                .filter(file -> !file.isEmpty())
                .map(file ->
                CompletableFuture.supplyAsync(() -> {
                    int displayOrder = orderCounter.incrementAndGet();
                    String key = fileKeyGenerator.generateChatMessageFileKey(conversationId, file.getOriginalFilename());
                    fileStorageService.uploadFile(file, key);

                    return FileMetaDataResponse.builder()
                            .url(fileStorageService.generatePermanentUrl(key))
                            .name(file.getOriginalFilename())
                            .contentType(file.getContentType())
                            .size(file.getSize())
                            .displayOrder(displayOrder)
                            .build();
                })
        ).toList();

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> futures.stream()
                .map(CompletableFuture::join)
                .toList());
    }

    @Override
    public List<String> uploadProjectFiles(Long projectId, List<MultipartFile> files) {
        List<CompletableFuture<String>> futures = files.stream().map(file ->
                CompletableFuture.supplyAsync(() -> {
                    String key = fileKeyGenerator.generateProjectFileKey(projectId, file.getOriginalFilename());
                    fileStorageService.uploadFile(file, key);
                    return key;
                })
        ).toList();

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        return futures.stream()
                .map(CompletableFuture::join)
                .toList();
    }
}
