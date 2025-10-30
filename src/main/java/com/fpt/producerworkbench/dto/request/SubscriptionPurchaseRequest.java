package com.fpt.producerworkbench.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubscriptionPurchaseRequest {

    @NotNull
    private Long proPackageId;

    private String returnUrl;
    private String cancelUrl;
}


