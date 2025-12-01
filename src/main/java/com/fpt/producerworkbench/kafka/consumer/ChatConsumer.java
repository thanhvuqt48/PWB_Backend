package com.fpt.producerworkbench.kafka.consumer;

import com.fpt.producerworkbench.dto.request.ChatRequest;
import com.fpt.producerworkbench.service.ChatMessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import static com.fpt.producerworkbench.constant.KafkaGroup.CHAT_GROUP;
import static com.fpt.producerworkbench.constant.KafkaTopic.CHAT_MESSAGE;


@Service
@RequiredArgsConstructor
@Slf4j(topic = "CHAT-CONSUMER")
public class ChatConsumer {

    private final ChatMessageService chatMessageService;

    @KafkaListener(topics = CHAT_MESSAGE, groupId = CHAT_GROUP)
    @Retryable(
            retryFor = Exception.class,
            maxAttempts = 4,
            backoff = @Backoff(delay = 500, multiplier = 2)
    )
    public void listenChatMessage(ChatRequest request, Acknowledgment acknowledgment) {
        chatMessageService.createMessage(request);
        acknowledgment.acknowledge();
    }

}
