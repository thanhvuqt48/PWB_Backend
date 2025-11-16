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
    String uploadBytes(byte[] bytes, String objectKey, String contentType);
    byte[] downloadBytes(String objectKey);
    
    /**
     * Upload file từ local filesystem lên S3
     */
    void uploadFile(File file, String objectKey, String contentType);
    
    /**
     * Download file từ S3 về local filesystem
     */
    void downloadFile(String objectKey, File destinationFile);
    
    /**
     * Tạo presigned URL cho UPLOAD (PUT request) lên S3
     * Method này dùng PutObjectRequest, không thay host sang CloudFront
     */
    String generateUploadPresignedUrl(String objectKey, String contentType, long expiresInSeconds);
    
    /**
     * Xóa tất cả files có prefix (thư mục) trên S3
     */
    void deletePrefix(String prefix);
    
    /**
     * Tạo CloudFront streaming URL cho HLS playback
     * Method này KHÔNG dùng S3 presigned URL, mà dùng CloudFront distribution domain
     * 
     * @param objectKey S3 object key (ví dụ: "audio/hls/123/index.m3u8")
     * @return CloudFront URL (ví dụ: "https://dxxxxxxxx.cloudfront.net/audio/hls/123/index.m3u8")
     */
    String generateStreamingUrl(String objectKey);
}