package com.fpt.producerworkbench.service;

import com.fpt.producerworkbench.entity.OwnerCompensationPayment;

import java.math.BigDecimal;

/**
 * Service xử lý thanh toán đền bù của Owner
 */
public interface OwnerCompensationPaymentService {
    
    /**
     * Tạo payment order cho Owner compensation qua PayOS
     */
    OwnerCompensationPayment createPaymentOrder(
        Long contractId,
        Long ownerId,
        BigDecimal amount,
        String description
    );
    
    /**
     * Xử lý webhook từ PayOS khi Owner đã thanh toán
     */
    void handlePaymentWebhook(String orderCode, String status);
}


