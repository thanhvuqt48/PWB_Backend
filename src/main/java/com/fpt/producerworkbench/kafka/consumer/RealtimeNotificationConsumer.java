package com.fpt.producerworkbench.kafka.consumer;

import com.fpt.producerworkbench.dto.event.RealtimeNotificationEvent;
import com.fpt.producerworkbench.dto.websocket.SystemNotification;
import com.fpt.producerworkbench.service.WebSocketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import static com.fpt.producerworkbench.constant.KafkaGroup.NOTIFICATION_GROUP;
import static com.fpt.producerworkbench.constant.KafkaTopic.REALTIME_NOTIFICATION;

@Service
@RequiredArgsConstructor
@Slf4j(topic = "REALTIME-NOTIFICATION-CONSUMER")
public class RealtimeNotificationConsumer {

    private final WebSocketService webSocketService;

    @KafkaListener(topics = REALTIME_NOTIFICATION, groupId = NOTIFICATION_GROUP)
    @Retryable(retryFor = Exception.class, maxAttempts = 4, backoff = @Backoff(delay = 500, multiplier = 2))
    public void listenRealtimeNotification(RealtimeNotificationEvent event, Acknowledgment acknowledgment) {
        log.info("Received realtime notification event for user {}: notificationId={}, type={}, title={}",
                event.getUserId(), event.getNotificationId(), event.getType(), event.getTitle());

        try {
            // Gửi realtime qua WebSocket (notification đã được lưu vào DB ở
            // NotificationService)
            SystemNotification wsNotification = SystemNotification.builder()
                    .type("INFO")
                    .title(event.getTitle())
                    .message(event.getMessage())
                    .requiresAction(event.getActionUrl() != null && !event.getActionUrl().isBlank())
                    .actionUrl(event.getActionUrl())
                    .data(java.util.Map.of(
                            "notificationId", event.getNotificationId(),
                            "type", event.getType().name(),
                            "relatedEntityType",
                            event.getRelatedEntityType() != null ? event.getRelatedEntityType().name() : "",
                            "relatedEntityId", event.getRelatedEntityId() != null ? event.getRelatedEntityId() : 0))
                    .build();

            webSocketService.sendToUser(event.getUserId(), "/queue/notifications", wsNotification);
            log.info("Notification sent via WebSocket to user: {}, notificationId: {}",
                    event.getUserId(), event.getNotificationId());

            acknowledgment.acknowledge();
        } catch (Exception e) {
            log.error("Failed to send notification via WebSocket for user {}: {}",
                    event.getUserId(), e.getMessage(), e);
            throw e; // Re-throw để Kafka retry
        }
    }
}
