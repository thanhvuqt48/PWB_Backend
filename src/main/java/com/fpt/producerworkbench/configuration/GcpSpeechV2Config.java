package com.fpt.producerworkbench.configuration;

import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.speech.v2.SpeechClient;
import com.google.cloud.speech.v2.SpeechSettings;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GcpSpeechV2Config {

    @Bean(name = "speechV2Client")
    @ConditionalOnMissingBean(name = "speechV2Client")
    public SpeechClient fallbackSpeechV2Client(GoogleCredentials creds, GcpProperties props) throws Exception {
        String location = props.getSpeech().getLocation() != null ? props.getSpeech().getLocation() : "global";
        String endpoint = "speech.googleapis.com:443";
        if (!"global".equalsIgnoreCase(location)) {
            endpoint = location + "-speech.googleapis.com:443";
        }
        SpeechSettings settings = SpeechSettings.newBuilder()
                .setCredentialsProvider(FixedCredentialsProvider.create(creds))
                .setEndpoint(endpoint)
                .build();
        return SpeechClient.create(settings);
    }
}
