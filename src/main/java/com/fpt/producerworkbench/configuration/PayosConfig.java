package com.fpt.producerworkbench.configuration;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import vn.payos.PayOS;

@Configuration
@RequiredArgsConstructor
public class PayosConfig {

    private final PayosProperties payosProperties;

    @Bean
    public vn.payos.PayOS payOS() {
        return new PayOS(
                payosProperties.getClientId(),
                payosProperties.getApiKey(),
                payosProperties.getChecksumKey()
        );
    }
}