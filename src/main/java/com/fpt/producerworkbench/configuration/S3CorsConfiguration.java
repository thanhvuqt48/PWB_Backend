package com.fpt.producerworkbench.configuration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.util.List;

@Component
@Slf4j
public class S3CorsConfiguration implements CommandLineRunner {

    private final S3Client s3Client;
    
    @Value("${aws.s3.bucket-name}")
    private String bucketName;
    
    @Value("#{T(java.util.Arrays).asList('${cors.allowed-origins}'.split(','))}")
    private List<String> allowedOrigins;

    public S3CorsConfiguration(S3Client s3Client) {
        this.s3Client = s3Client;
    }

    @Override
    public void run(String... args) {
        try {
            configureCors();
        } catch (Exception e) {
            log.error("❌ Failed to configure S3 CORS: {}", e.getMessage());
        }
    }

    private void configureCors() {
        log.info("🔧 Configuring CORS for S3 bucket: {}", bucketName);

        CORSRule corsRule = CORSRule.builder()
                .allowedHeaders(List.of("*"))
                .allowedMethods(List.of("GET", "PUT", "POST", "DELETE", "HEAD"))
                .allowedOrigins(allowedOrigins)
                .exposeHeaders(List.of("ETag", "x-amz-request-id", "Content-Length"))
                .maxAgeSeconds(3600)
                .build();

        CORSConfiguration corsConfiguration = CORSConfiguration.builder()
                .corsRules(corsRule)
                .build();

        PutBucketCorsRequest putBucketCorsRequest = PutBucketCorsRequest.builder()
                .bucket(bucketName)
                .corsConfiguration(corsConfiguration)
                .build();

        s3Client.putBucketCors(putBucketCorsRequest);
        
        log.info("✅ S3 CORS configured successfully for bucket: {}", bucketName);
        log.info("   Allowed origins: {}", allowedOrigins);
    }
}

