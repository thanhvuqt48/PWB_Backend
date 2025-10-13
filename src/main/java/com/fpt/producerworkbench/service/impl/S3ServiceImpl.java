package com.fpt.producerworkbench.service.impl;

import com.fpt.producerworkbench.configuration.AwsProperties;
import com.fpt.producerworkbench.exception.AppException;
import com.fpt.producerworkbench.exception.ErrorCode;
import com.fpt.producerworkbench.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.IOException;

@Service
@RequiredArgsConstructor
@Slf4j
public class S3ServiceImpl implements FileStorageService {

    private final S3Client s3Client;
    private final AwsProperties awsProperties;

    @Override
    public String uploadFile(MultipartFile file, String objectKey) {
        try {
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(awsProperties.getS3().getBucketName())
                    .key(objectKey)
                    .contentType(file.getContentType())
                    .build();

            s3Client.putObject(putObjectRequest, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));

            return objectKey;
        } catch (IOException e) {
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
            log.info("Successfully deleted file '{}' from bucket '{}'", objectKey, awsProperties.getS3().getBucketName());
        } catch (S3Exception e) {
            log.error("S3 Error deleting file '{}': {}", objectKey, e.getMessage());
            if (e.statusCode() == 404) {
                log.warn("File '{}' not found in bucket. Nothing to delete.", objectKey);
            } else {
                throw new AppException(ErrorCode.DELETE_FAILED);
            }
        } catch (Exception e) {
            log.error("Generic error deleting file '{}' from bucket '{}'", objectKey, awsProperties.getS3().getBucketName(), e);
            throw new AppException(ErrorCode.DELETE_FAILED);
        }
    }
}