package com.fpt.producerworkbench.dto.request;

import com.fpt.producerworkbench.common.NotificationType;
import com.fpt.producerworkbench.common.RelatedEntityType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SendNotificationRequest {
    private Long userId;
    private NotificationType type;
    private String title;
    private String message;
    private RelatedEntityType relatedEntityType;
    private Long relatedEntityId;
    private String actionUrl; // Optional field
}

