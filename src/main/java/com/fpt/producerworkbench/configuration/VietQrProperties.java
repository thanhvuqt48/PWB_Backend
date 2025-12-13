package com.fpt.producerworkbench.configuration;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "vietqr")
@Getter
@Setter
public class VietQrProperties {

    private String baseUrl;
    private String apiKey;
    private String clientId;
}
