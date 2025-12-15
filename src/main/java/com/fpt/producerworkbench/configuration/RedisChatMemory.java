package com.fpt.producerworkbench.configuration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Redis-backed implementation of Spring AI ChatMemory interface
 * 
 * Stores conversation history in Redis with TTL for automatic cleanup.
 * Uses simple DTO to avoid Jackson polymorphic deserialization issues.
 */
@Slf4j
public class RedisChatMemory implements ChatMemory {
    
    private final RedisTemplate<String, Object> redisTemplate;
    private final Duration ttl;
    private final String keyPrefix;
    private final ObjectMapper objectMapper;
    
    public RedisChatMemory(
            RedisTemplate<String, Object> redisTemplate,
            Duration ttl,
            String keyPrefix) {
        this.redisTemplate = redisTemplate;
        this.ttl = ttl;
        this.keyPrefix = keyPrefix;
        this.objectMapper = new ObjectMapper();
    }
    
    @Override
    public void add(String conversationId, List<Message> messages) {
        String key = keyPrefix + conversationId;
        
        try {
            // Get existing messages
            List<Message> existing = get(conversationId, Integer.MAX_VALUE);
            existing.addAll(messages);
            
            // Convert to DTOs
            List<MessageDTO> dtos = existing.stream()
                .map(MessageDTO::fromMessage)
                .collect(Collectors.toList());
            
            // Serialize to JSON
            String json = objectMapper.writeValueAsString(dtos);
            
            // Store in Redis with TTL
            redisTemplate.opsForValue().set(key, json, ttl);
            
            log.debug("Saved {} messages to conversation: {}", existing.size(), conversationId);
        } catch (Exception e) {
            log.error("Failed to save messages for conversation: {}", conversationId, e);
            throw new RuntimeException("Failed to save chat memory", e);
        }
    }
    
    @Override
    public List<Message> get(String conversationId, int lastN) {
        String key = keyPrefix + conversationId;
        
        try {
            String json = (String) redisTemplate.opsForValue().get(key);
            if (json == null) {
                return new ArrayList<>();
            }
            
            // Deserialize from JSON
            List<MessageDTO> dtos = objectMapper.readValue(json, 
                new TypeReference<List<MessageDTO>>() {});
            
            // Convert DTOs back to Messages
            List<Message> allMessages = dtos.stream()
                .map(MessageDTO::toMessage)
                .collect(Collectors.toList());
            
            // Return last N messages (sliding window)
            int size = allMessages.size();
            int fromIndex = Math.max(0, size - lastN);
            return allMessages.subList(fromIndex, size);
            
        } catch (Exception e) {
            log.error("Failed to retrieve messages for conversation: {}", conversationId, e);
            return new ArrayList<>();
        }
    }
    
    @Override
    public void clear(String conversationId) {
        String key = keyPrefix + conversationId;
        redisTemplate.delete(key);
        log.debug("Cleared conversation: {}", conversationId);
    }
    
    /**
     * Simple DTO for Redis serialization
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class MessageDTO {
        private String messageType;
        private String content;
        
        static MessageDTO fromMessage(Message message) {
            return new MessageDTO(
                message.getMessageType().getValue(),
                message.getContent()
            );
        }
        
        Message toMessage() {
            if ("user".equals(messageType)) {
                return new UserMessage(content);
            } else if ("assistant".equals(messageType)) {
                return new AssistantMessage(content);
            } else {
                // Fallback to user message
                return new UserMessage(content);
            }
        }
    }
}
