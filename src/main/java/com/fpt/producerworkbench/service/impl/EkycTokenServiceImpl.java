package com.fpt.producerworkbench.service.impl;

import com.fpt.producerworkbench.service.EkycTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j(topic = "EKYC-TOKEN-SERVICE")
public class EkycTokenServiceImpl implements EkycTokenService {

    private final RedisTemplate<String, Object> redisTemplate;
    private static final String EKYC_TOKEN_KEY = "vnpt:access_token";

    @Override
    public void saveToken(String accessToken, long expiresIn, TimeUnit timeUnit) {
        redisTemplate.opsForValue().set(EKYC_TOKEN_KEY, accessToken, expiresIn, timeUnit);
    }

    @Override
    public String getToken(String key) {
        return (String) redisTemplate.opsForValue().get(key);
    }
}
