//package com.fpt.producerworkbench.configuration;
//
//import com.google.api.gax.core.FixedCredentialsProvider;
//import com.google.auth.oauth2.GoogleCredentials;
//import com.google.cloud.speech.v1.SpeechSettings;
//import com.google.cloud.storage.Storage;
//import com.google.cloud.storage.StorageOptions;
//import lombok.RequiredArgsConstructor;
//import org.springframework.boot.context.properties.EnableConfigurationProperties;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.util.StringUtils;
//
//import java.io.InputStream;
//import java.nio.file.Files;
//import java.nio.file.Paths;
//import java.util.List;
//
//@Configuration
//@EnableConfigurationProperties(GcpProperties.class)
//@RequiredArgsConstructor
//public class GcpConfig {
//
//    private final GcpProperties props;
//
//    @Bean
//    public GoogleCredentials googleCredentials() throws Exception {
//        if (StringUtils.hasText(props.getCredentialsFile())) {
//            try (InputStream in = Files.newInputStream(Paths.get(props.getCredentialsFile()))) {
//                return GoogleCredentials.fromStream(in)
//                        .createScoped(List.of("https://www.googleapis.com/auth/cloud-platform"));
//            }
//        }
//        return GoogleCredentials.getApplicationDefault()
//                .createScoped(List.of("https://www.googleapis.com/auth/cloud-platform"));
//    }
//
//    @Bean
//    public Storage storage(GoogleCredentials creds) {
//        return StorageOptions.newBuilder()
//                .setProjectId(props.getProjectId())
//                .setCredentials(creds)
//                .build()
//                .getService();
//    }
//
//    // (Tuỳ chọn) Giữ v1 nếu nơi khác còn dùng
//    @Bean("speechV1Client")
//    public com.google.cloud.speech.v1.SpeechClient speechV1Client(GoogleCredentials creds) throws Exception {
//        SpeechSettings settings = SpeechSettings.newBuilder()
//                .setCredentialsProvider(FixedCredentialsProvider.create(creds))
//                .build();
//        return com.google.cloud.speech.v1.SpeechClient.create(settings);
//    }
//
//    // NEW: Speech-to-Text v2 client — endpoint theo location
//    @Bean("speechV2Client")
//    public com.google.cloud.speech.v2.SpeechClient speechV2Client(GoogleCredentials creds) throws Exception {
//        String location = props.getSpeech().getLocation() != null ? props.getSpeech().getLocation() : "global";
//        String endpoint = "global".equalsIgnoreCase(location)
//                ? "speech.googleapis.com:443"
//                : (location + "-speech.googleapis.com:443");
//
//        com.google.cloud.speech.v2.SpeechSettings settings =
//                com.google.cloud.speech.v2.SpeechSettings.newBuilder()
//                        .setCredentialsProvider(FixedCredentialsProvider.create(creds))
//                        .setEndpoint(endpoint)
//                        .build();
//        return com.google.cloud.speech.v2.SpeechClient.create(settings);
//    }
//}
package com.fpt.producerworkbench.configuration;

import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.speech.v1.SpeechSettings;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.util.List;

@Configuration
@EnableConfigurationProperties(GcpProperties.class)
@RequiredArgsConstructor
public class GcpConfig {

    private final GcpProperties props;

    // SỬA ĐỔI QUAN TRỌNG Ở ĐÂY:
    // Thêm tham số ResourceLoader để Spring tự động xử lý đường dẫn
    @Bean
    public GoogleCredentials googleCredentials(ResourceLoader resourceLoader) throws Exception {
        if (StringUtils.hasText(props.getCredentialsFile())) {
            // resourceLoader.getResource(...) hỗ trợ cả "classpath:" và "file:"
            Resource resource = resourceLoader.getResource(props.getCredentialsFile());

            try (InputStream in = resource.getInputStream()) {
                return GoogleCredentials.fromStream(in)
                        .createScoped(List.of("https://www.googleapis.com/auth/cloud-platform"));
            }
        }
        // Fallback về default nếu không cấu hình file
        return GoogleCredentials.getApplicationDefault()
                .createScoped(List.of("https://www.googleapis.com/auth/cloud-platform"));
    }

    @Bean
    public Storage storage(GoogleCredentials creds) {
        return StorageOptions.newBuilder()
                .setProjectId(props.getProjectId())
                .setCredentials(creds)
                .build()
                .getService();
    }

    // (Tuỳ chọn) Giữ v1 nếu nơi khác còn dùng
    @Bean("speechV1Client")
    public com.google.cloud.speech.v1.SpeechClient speechV1Client(GoogleCredentials creds) throws Exception {
        SpeechSettings settings = SpeechSettings.newBuilder()
                .setCredentialsProvider(FixedCredentialsProvider.create(creds))
                .build();
        return com.google.cloud.speech.v1.SpeechClient.create(settings);
    }

    // NEW: Speech-to-Text v2 client — endpoint theo location
    @Bean("speechV2Client")
    public com.google.cloud.speech.v2.SpeechClient speechV2Client(GoogleCredentials creds) throws Exception {
        String location = props.getSpeech().getLocation() != null ? props.getSpeech().getLocation() : "global";
        String endpoint = "global".equalsIgnoreCase(location)
                ? "speech.googleapis.com:443"
                : (location + "-speech.googleapis.com:443");

        com.google.cloud.speech.v2.SpeechSettings settings =
                com.google.cloud.speech.v2.SpeechSettings.newBuilder()
                        .setCredentialsProvider(FixedCredentialsProvider.create(creds))
                        .setEndpoint(endpoint)
                        .build();
        return com.google.cloud.speech.v2.SpeechClient.create(settings);
    }
}