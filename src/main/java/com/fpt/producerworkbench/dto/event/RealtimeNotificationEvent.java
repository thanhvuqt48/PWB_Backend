package com.fpt.producerworkbench.dto.event;

import com.fpt.producerworkbench.common.NotificationType;
import com.fpt.producerworkbench.common.RelatedEntityType;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Setter
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RealtimeNotificationEvent {
    Long notificationId; // ID của notification đã được lưu trong DB
    Long userId;
    NotificationType type;
    String title;
    String message;
    RelatedEntityType relatedEntityType;
    Long relatedEntityId;
    String actionUrl;
}

