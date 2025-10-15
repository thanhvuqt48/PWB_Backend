package com.fpt.producerworkbench.configuration;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.transfer.s3.S3TransferManager;

@Configuration
@RequiredArgsConstructor
public class AwsConfig {

    private final AwsProperties awsProperties;

    /**
     * S3Client đồng bộ, sử dụng cho các hoạt động đơn giản như xóa file.
     */
    @Bean
    public S3Client s3Client() {
        AwsBasicCredentials credentials = AwsBasicCredentials.create(
                awsProperties.getAccessKeyId(),
                awsProperties.getSecretAccessKey()
        );

        return S3Client.builder()
                .region(Region.of(awsProperties.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .build();
    }

    /**
     * S3AsyncClient bất đồng bộ, nền tảng cho S3TransferManager.
     * Cấu hình accelerate(true) để bật S3 Transfer Acceleration cho việc upload.
     */
    @Bean
    public S3AsyncClient s3AsyncClient() {
        AwsBasicCredentials credentials = AwsBasicCredentials.create(
                awsProperties.getAccessKeyId(),
                awsProperties.getSecretAccessKey()
        );

        return S3AsyncClient.builder()
                .region(Region.of(awsProperties.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .accelerate(true) // Bật Transfer Acceleration
                .build();
    }

    /**
     * S3TransferManager là tiện ích cấp cao để tối ưu hóa việc upload/download,
     * tự động quản lý multipart và thực thi song song.
     */
    @Bean
    public S3TransferManager s3TransferManager(S3AsyncClient s3AsyncClient) {
        return S3TransferManager.builder()
                .s3Client(s3AsyncClient)
                .build();
    }

    /**
     * S3Presigner là client chuyên dụng để tạo các URL được ký (presigned URLs),
     * phục vụ cho việc download an toàn và tối ưu.
     */
    @Bean
    public S3Presigner s3Presigner() {
        AwsBasicCredentials credentials = AwsBasicCredentials.create(
                awsProperties.getAccessKeyId(),
                awsProperties.getSecretAccessKey()
        );

        return S3Presigner.builder()
                .region(Region.of(awsProperties.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .build();
    }
}