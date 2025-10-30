package com.fpt.producerworkbench.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubscriptionUpgradeRequest {

    @NotNull
    private Long newProPackageId;

    private String returnUrl;
    private String cancelUrl;
}


