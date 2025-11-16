package com.fpt.producerworkbench;

import com.fpt.producerworkbench.configuration.AwsProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableSpringDataWebSupport(pageSerializationMode = EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO)
@SpringBootApplication
@EnableAsync
@EnableScheduling
@EnableConfigurationProperties(AwsProperties.class)
@EnableFeignClients
public class ProducerWorkbenchApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProducerWorkbenchApplication.class, args);
    }

}
