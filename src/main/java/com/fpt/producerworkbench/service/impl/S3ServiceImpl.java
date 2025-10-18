package com.fpt.producerworkbench.service.impl;

import com.fpt.producerworkbench.configuration.AwsProperties;
import com.fpt.producerworkbench.exception.AppException;
import com.fpt.producerworkbench.exception.ErrorCode;
import com.fpt.producerworkbench.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.transfer.s3.S3TransferManager;
import software.amazon.awssdk.transfer.s3.model.CompletedFileUpload;
import software.amazon.awssdk.transfer.s3.model.FileUpload;
import software.amazon.awssdk.transfer.s3.model.UploadFileRequest;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.Duration;

@Service
@RequiredArgsConstructor
@Slf4j
public class S3ServiceImpl implements FileStorageService {

    private final S3Client s3Client;
    private final S3TransferManager s3TransferManager;
    private final S3Presigner s3Presigner;
    private final AwsProperties awsProperties;

    @Value("${cloudfront.domain}")
    private String cloudfrontDomain;

    @Override
    public String uploadFile(MultipartFile multipartFile, String objectKey) {
        File file = convertMultiPartToFile(multipartFile);

        try {
            UploadFileRequest uploadRequest = UploadFileRequest.builder()
                    .putObjectRequest(req -> req
                            .bucket(awsProperties.getS3().getBucketName())
                            .key(objectKey)
                            .contentType(multipartFile.getContentType()))
                    .source(file.toPath())
                    .build();

            FileUpload fileUpload = s3TransferManager.uploadFile(uploadRequest);

            CompletedFileUpload completedUpload = fileUpload.completionFuture().join();
            log.info("Upload thành công file '{}'. ETag: {}", objectKey, completedUpload.response().eTag());

            return objectKey;

        } catch (Exception e) {
            log.error("Lỗi không xác định khi upload file '{}': {}", objectKey, e.getMessage());
            throw new AppException(ErrorCode.UPLOAD_FAILED);
        } finally {
            if (file.exists()) {
                file.delete();
            }
        }
    }

    @Override
    public String uploadBytes(byte[] bytes, String objectKey, String contentType) {
        try {
            java.nio.file.Path tempPath = java.nio.file.Files.createTempFile("pwb-upload-", ".tmp");
            java.nio.file.Files.write(tempPath, bytes);

            UploadFileRequest uploadRequest = UploadFileRequest.builder()
                    .putObjectRequest(req -> req
                            .bucket(awsProperties.getS3().getBucketName())
                            .key(objectKey)
                            .contentType(contentType))
                    .source(tempPath)
                    .build();

            FileUpload fileUpload = s3TransferManager.uploadFile(uploadRequest);
            CompletedFileUpload completedUpload = fileUpload.completionFuture().join();
            log.info("Upload bytes thành công '{}' ETag: {}", objectKey, completedUpload.response().eTag());

            java.nio.file.Files.deleteIfExists(tempPath);
            return objectKey;
        } catch (Exception e) {
            log.error("Lỗi upload bytes '{}' : {}", objectKey, e.getMessage());
            throw new AppException(ErrorCode.UPLOAD_FAILED);
        }
    }

    @Override
    public void deleteFile(String objectKey) {
        try {
            DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                    .bucket(awsProperties.getS3().getBucketName())
                    .key(objectKey)
                    .build();
            s3Client.deleteObject(deleteObjectRequest);
            log.info("Đã xóa thành công file '{}' khỏi bucket '{}'", objectKey, awsProperties.getS3().getBucketName());
        } catch (Exception e) {
            log.error("Lỗi khi xóa file '{}': {}", objectKey, e.getMessage());
            throw new AppException(ErrorCode.DELETE_FAILED);
        }
    }

    @Override
    public String generatePresignedUrl(String objectKey, boolean forDownload, String fileName) {
        try {
            // Xây dựng GetObjectRequest builder
            GetObjectRequest.Builder requestBuilder = GetObjectRequest.builder()
                    .bucket(awsProperties.getS3().getBucketName())
                    .key(objectKey);

            // Thêm Content-Disposition dựa trên yêu cầu
            if (forDownload) {
                // Buộc trình duyệt tải xuống file với tên gốc
                String disposition = "attachment; filename=\"" + fileName + "\"";
                requestBuilder.responseContentDisposition(disposition);
            } else {
                // Yêu cầu trình duyệt hiển thị file (nếu có thể)
                requestBuilder.responseContentDisposition("inline");
            }

            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofMinutes(15))
                    .getObjectRequest(requestBuilder.build())
                    .build();

            String presignedUrl = s3Presigner.presignGetObject(presignRequest).url().toString();
            log.info("Đã tạo presigned URL cho key '{}' với chế độ: {}", objectKey, forDownload ? "Download" : "View");

            // Tối ưu: Thay thế domain S3 bằng domain CloudFront
            String s3Host = String.format("%s.s3.%s.amazonaws.com",
                    awsProperties.getS3().getBucketName(),
                    awsProperties.getRegion());

            if (cloudfrontDomain != null && !cloudfrontDomain.isBlank()) {
                return presignedUrl.replace(s3Host, cloudfrontDomain);
            }

            return presignedUrl;

        } catch (Exception e) {
            log.error("Lỗi khi tạo presigned URL cho file '{}': {}", objectKey, e.getMessage());
            throw new AppException(ErrorCode.URL_GENERATION_FAILED);
        }
    }

    @Override
    public byte[] downloadBytes(String objectKey) {
        try {
            var resp = s3Client.getObjectAsBytes(b -> b
                    .bucket(awsProperties.getS3().getBucketName())
                    .key(objectKey));
            return resp.asByteArray();
        } catch (Exception e) {
            log.error("Lỗi download '{}' từ S3: {}", objectKey, e.getMessage());
            throw new AppException(ErrorCode.STORAGE_READ_FAILED);
        }
    }

    /**
     * Chuyển đổi MultipartFile thành File tạm để S3TransferManager có thể đọc.
     * Đây là cách hiệu quả để xử lý file mà không tốn nhiều bộ nhớ.
     */
    private File convertMultiPartToFile(MultipartFile multipartFile) {
        File convFile = new File(System.getProperty("java.io.tmpdir") + "/" + multipartFile.getOriginalFilename());
        try (FileOutputStream fos = new FileOutputStream(convFile)) {
            fos.write(multipartFile.getBytes());
        } catch (IOException e) {
            log.error("Lỗi chuyển đổi MultipartFile thành File: {}", e.getMessage());
            throw new AppException(ErrorCode.UPLOAD_FAILED);
        }
        return convFile;
    }
}