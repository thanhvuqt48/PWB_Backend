package com.fpt.producerworkbench.service;

import com.fpt.producerworkbench.dto.request.SubscriptionPurchaseRequest;
import com.fpt.producerworkbench.dto.request.SubscriptionUpgradeRequest;
import com.fpt.producerworkbench.dto.response.SubscriptionActionResponse;
import com.fpt.producerworkbench.dto.response.SubscriptionStatusResponse;

public interface ProSubscriptionService {
    SubscriptionActionResponse purchase(Long userId, SubscriptionPurchaseRequest request);
    SubscriptionActionResponse upgrade(Long userId, SubscriptionUpgradeRequest request);
    void cancelAutoRenew(Long userId);
    void reactivateAutoRenew(Long userId);
    SubscriptionStatusResponse getStatus(Long userId);
    void handleWebhook(String body);
}


