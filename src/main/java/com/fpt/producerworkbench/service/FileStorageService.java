package com.fpt.producerworkbench.service;

import org.springframework.web.multipart.MultipartFile;

public interface FileStorageService {
    String uploadFile(MultipartFile file, String objectKey);
    void deleteFile(String objectKey);
}