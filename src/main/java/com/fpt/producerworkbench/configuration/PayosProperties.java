package com.fpt.producerworkbench.configuration;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "payos")
@Getter
@Setter
public class PayosProperties {

    private String clientId;
    private String apiKey;
    private String checksumKey;

    private String returnUrl;
    private String cancelUrl;
}