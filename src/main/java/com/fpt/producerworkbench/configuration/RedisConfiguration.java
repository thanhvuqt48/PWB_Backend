package com.fpt.producerworkbench.configuration;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

@Configuration
public class RedisConfiguration {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new StringRedisSerializer());

        return template;
    }
    
    /**
     * Redis-backed ChatMemory for Spring AI conversations
     * TTL: 24 hours (matches WebSocket session TTL)
     * Key prefix: "ai:chat:" (unique to avoid conflicts)
     */
    @Bean
    public ChatMemory redisChatMemory(RedisTemplate<String, Object> redisTemplate) {
        return new RedisChatMemory(
            redisTemplate,
            Duration.ofHours(24),
            "ai:chat:"
        );
    }
}
