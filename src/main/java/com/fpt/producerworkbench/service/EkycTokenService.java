package com.fpt.producerworkbench.service;

import java.util.concurrent.TimeUnit;

public interface EkycTokenService {

    void saveToken(String accessToken, long expiresIn, TimeUnit timeUnit);

    String getToken(String key);
}
