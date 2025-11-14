package com.fpt.producerworkbench.configuration;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;

@Configuration
@RequiredArgsConstructor
public class BedrockConfiguration {

    private final AwsProperties awsProperties;

    @Bean
    public BedrockRuntimeClient bedrockRuntimeClient(
            org.springframework.core.env.Environment env
    ) {

        String region = env.getProperty("bedrock.region",
                (awsProperties.getRegion() == null ? "ap-southeast-1" : awsProperties.getRegion()));
        return BedrockRuntimeClient.builder()
                .region(Region.of(region))
                .build();
    }
}
