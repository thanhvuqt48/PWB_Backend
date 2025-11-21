//package com.fpt.producerworkbench.configuration;
//
//import org.springframework.context.annotation.Configuration;
//import org.springframework.context.annotation.Bean;
//import org.springframework.scheduling.annotation.EnableAsync;
//import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
//
//import java.util.concurrent.Executor;
//
//@Configuration
//@EnableAsync
//public class AsyncConfig {
//
//    @Bean(name = { "pwbTaskExecutor", "taskExecutor" })
//    public Executor pwbTaskExecutor() {
//        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
//        ex.setCorePoolSize(4);
//        ex.setMaxPoolSize(8);
//        ex.setQueueCapacity(200);
//        ex.setThreadNamePrefix("pwb-async-");
//        ex.setAllowCoreThreadTimeOut(true);
//        ex.initialize();
//        return ex;
//    }
//}
package com.fpt.producerworkbench.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean("taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setThreadNamePrefix("pwb-async-");
        ex.setCorePoolSize(2);
        ex.setMaxPoolSize(4);
        ex.setQueueCapacity(100);
        ex.initialize();
        return ex;
    }
}
