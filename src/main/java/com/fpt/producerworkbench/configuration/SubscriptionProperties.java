package com.fpt.producerworkbench.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "subscription")
public class SubscriptionProperties {
    private int graceDays = 5;
    private String renewalEmailSubject = "Gia hạn gói PRO";
}


