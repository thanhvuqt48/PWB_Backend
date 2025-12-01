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
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
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
    public void deleteFile(String objectKeyOrUrl) {
        try {
            String s3Key = extractS3KeyFromUrl(objectKeyOrUrl);
            
            DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                    .bucket(awsProperties.getS3().getBucketName())
                    .key(s3Key)
                    .build();
            s3Client.deleteObject(deleteObjectRequest);
            log.info("Đã xóa thành công file '{}' khỏi bucket '{}'", s3Key, awsProperties.getS3().getBucketName());
        } catch (Exception e) {
            log.error("Lỗi khi xóa file '{}': {}", objectKeyOrUrl, e.getMessage());
            throw new AppException(ErrorCode.DELETE_FAILED);
        }
    }

    @Override
    public String extractS3KeyFromUrl(String urlOrKey) {
        if (urlOrKey == null || urlOrKey.isEmpty()) {
            throw new AppException(ErrorCode.INVALID_FILE_KEY);
        }

        int queryIndex = urlOrKey.indexOf('?');
        int fragmentIndex = urlOrKey.indexOf('#');
        int endIndex = urlOrKey.length();
        if (queryIndex > 0) {
            endIndex = queryIndex;
        } else if (fragmentIndex > 0) {
            endIndex = fragmentIndex;
        }
        String cleanUrl = urlOrKey.substring(0, endIndex);

        String key = null;

        if (cleanUrl.contains("cloudfront.net")) {
            int domainIndex = cleanUrl.indexOf("cloudfront.net");
            String afterDomain = cleanUrl.substring(domainIndex + "cloudfront.net".length());
            if (afterDomain.startsWith("/")) {
                afterDomain = afterDomain.substring(1);
            }
            key = afterDomain.isEmpty() ? null : afterDomain;
        } 
        else if (cleanUrl.contains("amazonaws.com/")) {
            String[] parts = cleanUrl.split("amazonaws.com/");
            if (parts.length > 1) {
                key = parts[1];
            }
        } 
        else if (!cleanUrl.startsWith("http://") && !cleanUrl.startsWith("https://")) {
            key = cleanUrl;
        }

        if (key == null) {
            throw new AppException(ErrorCode.INVALID_FILE_KEY);
        }

        if (key.startsWith("/")) {
            key = key.substring(1);
        }

        return key;
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

    @Override
    public void uploadFile(File file, String objectKey, String contentType) {
        try {
            UploadFileRequest uploadRequest = UploadFileRequest.builder()
                    .putObjectRequest(req -> req
                            .bucket(awsProperties.getS3().getBucketName())
                            .key(objectKey)
                            .contentType(contentType))
                    .source(file.toPath())
                    .build();

            FileUpload fileUpload = s3TransferManager.uploadFile(uploadRequest);
            CompletedFileUpload completedUpload = fileUpload.completionFuture().join();
            
            log.info("Upload file thành công: {} -> {} (ETag: {})", 
                    file.getName(), objectKey, completedUpload.response().eTag());
                    
        } catch (Exception e) {
            log.error("Lỗi khi upload file '{}' lên S3: {}", objectKey, e.getMessage());
            throw new AppException(ErrorCode.UPLOAD_FAILED);
        }
    }

    @Override
    public void downloadFile(String objectKey, File destinationFile) {
        try {
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(awsProperties.getS3().getBucketName())
                    .key(objectKey)
                    .build();

            try (var s3Object = s3Client.getObject(getObjectRequest);
                 var outputStream = new FileOutputStream(destinationFile)) {
                s3Object.transferTo(outputStream);
            }

            log.info("Download file thành công: {} -> {} ({} bytes)", 
                    objectKey, destinationFile.getName(), destinationFile.length());
                    
        } catch (Exception e) {
            log.error("Lỗi khi download file '{}' từ S3: {}", objectKey, e.getMessage());
            throw new AppException(ErrorCode.STORAGE_READ_FAILED);
        }
    }

    @Override
    public String generateUploadPresignedUrl(String objectKey, String contentType, long expiresInSeconds) {
        try {
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(awsProperties.getS3().getBucketName())
                    .key(objectKey)
                    .contentType(contentType)
                    .build();

            PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofSeconds(expiresInSeconds))
                    .putObjectRequest(putObjectRequest)
                    .build();

            String url = s3Presigner.presignPutObject(presignRequest).url().toString();
            log.info("Đã tạo presigned UPLOAD URL cho key '{}' (expires in {}s)", objectKey, expiresInSeconds);
            return url;

        } catch (Exception e) {
            log.error("Lỗi khi tạo presigned upload URL cho file '{}': {}", objectKey, e.getMessage());
            throw new AppException(ErrorCode.URL_GENERATION_FAILED);
        }
    }

    @Override
    public void deletePrefix(String prefix) {
        try {
            log.info("Bắt đầu xóa tất cả files với prefix: {}", prefix);
            
            ListObjectsV2Request listRequest = ListObjectsV2Request.builder()
                    .bucket(awsProperties.getS3().getBucketName())
                    .prefix(prefix)
                    .build();

            ListObjectsV2Response listResponse;
            int totalDeleted = 0;
            
            do {
                listResponse = s3Client.listObjectsV2(listRequest);
                
                for (S3Object s3Object : listResponse.contents()) {
                    DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
                            .bucket(awsProperties.getS3().getBucketName())
                            .key(s3Object.key())
                            .build();
                    s3Client.deleteObject(deleteRequest);
                    totalDeleted++;
                    log.debug("Đã xóa: {}", s3Object.key());
                }
                
                // Nếu có nhiều hơn 1000 objects, cần pagination
                listRequest = listRequest.toBuilder()
                        .continuationToken(listResponse.nextContinuationToken())
                        .build();
                        
            } while (listResponse.isTruncated());
            
            log.info("Đã xóa thành công {} files với prefix: {}", totalDeleted, prefix);
            
        } catch (Exception e) {
            log.error("Lỗi khi xóa prefix '{}': {}", prefix, e.getMessage());
            throw new AppException(ErrorCode.DELETE_FAILED);
        }
    }

    @Override
    public String generateStreamingUrl(String objectKey) {
        if (cloudfrontDomain == null || cloudfrontDomain.isBlank()) {
            log.error("CloudFront domain chưa được cấu hình (cloudfront.domain = null hoặc blank)");
            throw new AppException(ErrorCode.URL_GENERATION_FAILED);
        }

        String normalizedDomain = cloudfrontDomain.trim();
        
        if (!normalizedDomain.startsWith("http://") && !normalizedDomain.startsWith("https://")) {
            normalizedDomain = "https://" + normalizedDomain;
        }
        
        if (!normalizedDomain.endsWith("/")) {
            normalizedDomain = normalizedDomain + "/";
        }

        String streamingUrl = normalizedDomain + objectKey;
        
        log.info("Đã tạo CloudFront streaming URL cho key '{}': {}", objectKey, streamingUrl);
        
        return streamingUrl;
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

    @Override
    public String generatePresignedUploadUrl(String objectKey, String contentType, Duration expiration) {
        try {
            PutObjectRequest put = PutObjectRequest.builder()
                    .bucket(awsProperties.getS3().getBucketName())
                    .key(objectKey)
                    .contentType(contentType)
                    .build();

            PutObjectPresignRequest req = PutObjectPresignRequest.builder()
                    .signatureDuration(expiration == null ? Duration.ofMinutes(15) : expiration)
                    .putObjectRequest(put)
                    .build();

            String url = s3Presigner.presignPutObject(req).url().toString();
            return url;
        } catch (Exception e) {
            log.error("Presigned PUT error for {}: {}", objectKey, e.getMessage());
            throw new AppException(ErrorCode.URL_GENERATION_FAILED);
        }
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

