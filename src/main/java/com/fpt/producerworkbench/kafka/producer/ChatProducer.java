package com.fpt.producerworkbench.kafka.producer;

import com.fpt.producerworkbench.dto.request.ChatRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import static com.fpt.producerworkbench.constant.KafkaTopic.CHAT_MESSAGE;

@Service
@RequiredArgsConstructor
@Slf4j(topic = "CHAT-PRODUCER")
public class ChatProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Retryable(
            retryFor = {Exception.class},
            maxAttempts = 4,
            backoff = @Backoff(delay = 500, multiplier = 2)
    )
    public void sendChatMessage(ChatRequest request) {
        kafkaTemplate.send(CHAT_MESSAGE, request.getSender().toString(), request)
                .whenComplete((result, ex) -> {
                    if (ex == null) {
                        log.info("Message sent successfully to topic {} for sender: {}", CHAT_MESSAGE, request.getSender());
                    } else {
                        log.error("Failed to send message to topic {} for sender: {}. Error: {}", CHAT_MESSAGE, request.getSender(), ex.getMessage());
                    }
                });
    }

}
