package com.fpt.producerworkbench.service;

import com.fpt.producerworkbench.dto.response.FileMetaDataResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface FileStorageService {
    String uploadFile(MultipartFile file, String objectKey);
    List<FileMetaDataResponse> uploadFiles(List<MultipartFile> files, String objectKey);
    void deleteFile(String objectKey);
    String generatePresignedUrl(String objectKey, boolean forDownload, String fileName);
    String uploadBytes(byte[] bytes, String objectKey, String contentType);
    byte[] downloadBytes(String objectKey);
}