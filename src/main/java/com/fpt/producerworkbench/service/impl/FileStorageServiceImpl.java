package com.fpt.producerworkbench.service.impl;

import com.fpt.producerworkbench.configuration.AwsProperties;
import com.fpt.producerworkbench.dto.response.FileMetaDataResponse;
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
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class FileStorageServiceImpl implements FileStorageService {

    private final S3Client s3Client;
    private final S3TransferManager s3TransferManager;
    private final S3Presigner s3Presigner;
    private final AwsProperties awsProperties;

    @Value("${cloudfront.domain}")
    private String cloudfrontDomain;

    @Override
    public String uploadFile(MultipartFile multipartFile, String objectKey) {

        validateUploadFile(multipartFile);
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
    public List<FileMetaDataResponse> uploadFiles(List<MultipartFile> files, String objectKey) {
        if (files == null || files.isEmpty()) {
            log.warn("Upload files is null or empty");
            return new ArrayList<>();
        }

        List<FileMetaDataResponse> fileMetaData = new ArrayList<>();
        String bucketName = awsProperties.getS3().getBucketName();

        for (MultipartFile file : files) {
            File fileConverted = convertMultiPartToFile(file);
            validateUploadFile(file);

            try {
                UploadFileRequest uploadRequest = UploadFileRequest.builder()
                        .putObjectRequest(req -> req
                                .bucket(bucketName)
                                .key(objectKey)
                                .contentType(file.getContentType()))
                        .source(fileConverted.toPath())
                        .build();

                FileUpload fileUpload = s3TransferManager.uploadFile(uploadRequest);

                CompletedFileUpload completedUpload = fileUpload.completionFuture().join();
                log.info("Upload thành công file '{}'. ETag: {}", objectKey, completedUpload.response().eTag());
            } catch (Exception e) {
                log.error("Lỗi khi tải file '{}': {}", file.getOriginalFilename(), e.getMessage());
                throw new AppException(ErrorCode.UPLOAD_FAILED);
            }

        }

        return fileMetaData;
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
            GetObjectRequest.Builder requestBuilder = GetObjectRequest.builder()
                    .bucket(awsProperties.getS3().getBucketName())
                    .key(objectKey);

            if (forDownload) {
                String disposition = "attachment; filename=\"" + fileName + "\"";
                requestBuilder.responseContentDisposition(disposition);
            } else {
                requestBuilder.responseContentDisposition("inline");
            }

            Duration expiration = forDownload ? Duration.ofMinutes(15) : Duration.ofHours(24);

            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                    .signatureDuration(expiration)
                    .getObjectRequest(requestBuilder.build())
                    .build();

            String presignedUrl = s3Presigner.presignGetObject(presignRequest).url().toString();
            log.info("Đã tạo presigned URL cho key '{}' với chế độ: {}", objectKey, forDownload ? "Download" : "View");

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
    public String generatePermanentUrl(String objectKey) {
        try {

            if (objectKey == null || objectKey.isBlank()) {
                log.error("Lỗi generatePermanentUrl(): objectKey bị null hoặc rỗng.");
                throw new AppException(ErrorCode.INVALID_FILE_KEY);
            }

            String normalizedKey = objectKey.startsWith("/")
                    ? objectKey.substring(1)
                    : objectKey;

            if (cloudfrontDomain != null && !cloudfrontDomain.isBlank()) {
                String permanentUrl = cloudfrontDomain.replaceAll("/+$", "")
                        + "/" + normalizedKey;

                log.debug("Tạo permanent URL bằng CloudFront thành công: {}", permanentUrl);
                return permanentUrl;
            }

            String permanentUrl = String.format(
                    "https://%s.s3.%s.amazonaws.com/%s",
                    awsProperties.getS3().getBucketName(),
                    awsProperties.getRegion(),
                    normalizedKey);

            log.debug("Tạo permanent URL bằng S3 thành công: {}", permanentUrl);
            return permanentUrl;

        } catch (AppException e) {
            log.error("Lỗi nghiệp vụ trong generatePermanentUrl(): {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error(
                    "Lỗi không mong muốn khi tạo permanent URL cho key '{}': {}",
                    objectKey,
                    e.getMessage(),
                    e);
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

    private void validateUploadFile(MultipartFile file) {
        if (file.isEmpty()) {
            log.warn("Bỏ qua file rỗng: {}", file.getOriginalFilename());
            return;
        }

        if (file.getSize() > awsProperties.getS3().getMaxFileSize()) {
            log.warn("Kích cỡ file tải lên vượt quá giới hạn: {} > {}", file.getSize(),
                    awsProperties.getS3().getMaxFileSize());
            throw new RuntimeException("Kích cỡ file tải lên vượt quá giới hạn.");
        }
    }
}
