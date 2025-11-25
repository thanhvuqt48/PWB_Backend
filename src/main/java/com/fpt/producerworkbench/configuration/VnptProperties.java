package com.fpt.producerworkbench.configuration;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "vnpt.ekyc")
@Getter
@Setter
public class VnptProperties {
    private String baseUrl;
    private String tokenId;
    private String tokenKey;
    private String accessToken;
}
