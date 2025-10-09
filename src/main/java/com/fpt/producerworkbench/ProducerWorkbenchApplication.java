package com.fpt.producerworkbench;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableSpringDataWebSupport(pageSerializationMode = EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO)
@SpringBootApplication
@EnableAsync
@EnableFeignClients
public class ProducerWorkbenchApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProducerWorkbenchApplication.class, args);
    }

}
