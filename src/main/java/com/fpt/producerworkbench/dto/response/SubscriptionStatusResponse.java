package com.fpt.producerworkbench.dto.response;

import com.fpt.producerworkbench.common.SubscriptionStatus;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubscriptionStatusResponse {
    private SubscriptionStatus status;
    private String planName;
    private LocalDateTime endDate;
    private boolean autoRenewEnabled;
    private LocalDateTime graceUntil;
}


