package com.fpt.producerworkbench.configuration;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "aws")
public class AwsProperties {
    private String region;
    private String accessKeyId;
    private String secretAccessKey;
    private S3 s3 = new S3();

    @Getter
    @Setter
    public static class S3 {
        private String bucketName;
        private Long maxFileSize = 10L * 1024 * 1024; // 10MB
    }
}