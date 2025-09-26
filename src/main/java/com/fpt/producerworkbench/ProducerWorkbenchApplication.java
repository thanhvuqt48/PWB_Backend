package com.fpt.producerworkbench;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class ProducerWorkbenchApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProducerWorkbenchApplication.class, args);
    }

}
