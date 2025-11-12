package com.fpt.producerworkbench.dto.websocket;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemNotification {

    private String type; // INFO, WARNING, ERROR, SUCCESS
    private String title;
    private String message;
    private Boolean requiresAction;
    private String actionUrl;
    private Map<String, Object> data; // ✅ Additional data payload
}
