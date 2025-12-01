package com.fpt.producerworkbench.service;

import com.fpt.producerworkbench.dto.response.FileMetaDataResponse;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.List;

public interface FileStorageService {
    String uploadFile(MultipartFile file, String objectKey);

    List<FileMetaDataResponse> uploadFiles(List<MultipartFile> files, String objectKey);

    void deleteFile(String objectKey);

    String generatePresignedUrl(String objectKey, boolean forDownload, String fileName);

    String generatePermanentUrl(String objectKey);

    String uploadBytes(byte[] bytes, String objectKey, String contentType);

    byte[] downloadBytes(String objectKey);

    String generatePresignedUploadUrl(String objectKey, String contentType, java.time.Duration expiration);

    void uploadFile(File file, String objectKey, String contentType);

    void downloadFile(String objectKey, File destinationFile);

    String generateUploadPresignedUrl(String objectKey, String contentType, long expiresInSeconds);

    void deletePrefix(String prefix);

    String generateStreamingUrl(String objectKey);

    String extractS3KeyFromUrl(String urlOrKey);
}