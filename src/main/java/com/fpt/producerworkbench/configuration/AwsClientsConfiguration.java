package com.fpt.producerworkbench.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.transcribe.TranscribeClient;

@Configuration
public class AwsClientsConfiguration {

    @Bean
    public TranscribeClient transcribeClient(AwsProperties props) {
        return TranscribeClient.builder()
                .region(Region.of(props.getRegion()))
                .build();
    }
}
