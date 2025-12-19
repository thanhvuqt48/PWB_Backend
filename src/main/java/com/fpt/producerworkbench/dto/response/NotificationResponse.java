package com.fpt.producerworkbench.dto.response;

import com.fpt.producerworkbench.common.NotificationType;
import com.fpt.producerworkbench.common.RelatedEntityType;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;

@Getter
@Setter
@Builder
public class NotificationResponse {
    private Long id;
    private NotificationType type;
    private String title;
    private String message;
    private Boolean isRead;
    private RelatedEntityType relatedEntityType;
    private Long relatedEntityId;
    private String actionUrl;
    private Date createdAt;
}

