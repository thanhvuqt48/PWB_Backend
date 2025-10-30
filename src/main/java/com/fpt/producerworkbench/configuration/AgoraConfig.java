package com.fpt.producerworkbench.configuration;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

@Data
@Configuration
@ConfigurationProperties(prefix = "agora")
@Component
public class AgoraConfig {
    @Value("${agora.app.id}")
    private String appId;
    private String appCertificate;

    public String getAppId() {
        return appId;
    }

}