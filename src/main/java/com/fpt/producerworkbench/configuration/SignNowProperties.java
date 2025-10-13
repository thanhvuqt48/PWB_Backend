package com.fpt.producerworkbench.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "signnow")
public class SignNowProperties {

    private String baseUrl;
    private String clientId;
    private String clientSecret;

    @Data
    public static class Auth {
        private String username;
        private String password;
    }

    @Data
    public static class Http {
        private int connectTimeoutMs = 10000;
        private int readTimeoutMs = 20000;
    }

    private Auth auth = new Auth();
    private Http http = new Http();
}
