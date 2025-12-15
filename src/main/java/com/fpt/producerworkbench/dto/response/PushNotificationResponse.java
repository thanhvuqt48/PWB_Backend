package com.fpt.producerworkbench.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for push notification operations
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PushNotificationResponse {

    private int successCount;
    private int failureCount;
    private String message;

    public static PushNotificationResponse success(int count) {
        return PushNotificationResponse.builder()
                .successCount(count)
                .failureCount(0)
                .message("Push notification sent successfully")
                .build();
    }

    public static PushNotificationResponse partial(int successCount, int failureCount) {
        return PushNotificationResponse.builder()
                .successCount(successCount)
                .failureCount(failureCount)
                .message("Push notification sent with some failures")
                .build();
    }

    public static PushNotificationResponse failure(String message) {
        return PushNotificationResponse.builder()
                .successCount(0)
                .failureCount(0)
                .message(message)
                .build();
    }
}
