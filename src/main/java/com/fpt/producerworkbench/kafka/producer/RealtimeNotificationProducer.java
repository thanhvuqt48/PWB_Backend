package com.fpt.producerworkbench.kafka.producer;

import com.fpt.producerworkbench.dto.event.RealtimeNotificationEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import static com.fpt.producerworkbench.constant.KafkaTopic.REALTIME_NOTIFICATION;

@Service
@RequiredArgsConstructor
@Slf4j(topic = "REALTIME-NOTIFICATION-PRODUCER")
public class RealtimeNotificationProducer {

    private final KafkaTemplate<String, RealtimeNotificationEvent> kafkaTemplate;

    @Retryable(
            retryFor = {Exception.class},
            maxAttempts = 4,
            backoff = @Backoff(delay = 500, multiplier = 2)
    )
    public void sendNotification(RealtimeNotificationEvent event) {
        kafkaTemplate.send(REALTIME_NOTIFICATION, event.getUserId().toString(), event)
                .whenComplete((result, ex) -> {
                    if (ex == null) {
                        log.info("Notification event sent successfully to topic {} for user: {}", 
                                REALTIME_NOTIFICATION, event.getUserId());
                    } else {
                        log.error("Failed to send notification event to topic {} for user: {}. Error: {}", 
                                REALTIME_NOTIFICATION, event.getUserId(), ex.getMessage());
                    }
                });
    }
}

