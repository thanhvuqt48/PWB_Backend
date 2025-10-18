package com.fpt.producerworkbench.service;

import org.springframework.web.multipart.MultipartFile;

public interface FileStorageService {
    String uploadFile(MultipartFile file, String objectKey);
    void deleteFile(String objectKey);
    String generatePresignedUrl(String objectKey, boolean forDownload, String fileName);
    String uploadBytes(byte[] bytes, String objectKey, String contentType);
    byte[] downloadBytes(String objectKey);
}