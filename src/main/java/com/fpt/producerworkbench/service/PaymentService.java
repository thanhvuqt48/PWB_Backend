package com.fpt.producerworkbench.service;

import com.fpt.producerworkbench.dto.request.PaymentRequest;
import com.fpt.producerworkbench.dto.response.PaymentResponse;
import com.fpt.producerworkbench.dto.response.PaymentStatusResponse;
import com.fpt.producerworkbench.dto.response.PaymentLatestResponse;
import com.fpt.producerworkbench.entity.OwnerCompensationPayment;

import java.math.BigDecimal;

public interface PaymentService {

    PaymentResponse createPayment(Long userId, Long projectId, Long contractId, PaymentRequest paymentRequest);

    PaymentResponse createAddendumPayment(Long userId, Long projectId, Long contractId, PaymentRequest paymentRequest);

    /**
     * Tạo payment order cho Owner compensation (Owner trả tiền đền bù Team khi terminate contract)
     */
    OwnerCompensationPayment createOwnerCompensationPayment(
            Long contractId,
            Long ownerId,
            BigDecimal amount,
            String description,
            String returnUrl,  // Optional, null thì dùng default
            String cancelUrl,  // Optional, null thì dùng default
            String terminationReason  // Optional, lý do chấm dứt từ FE request
    );

    void handlePaymentWebhook(String rawBody);

    PaymentStatusResponse getPaymentStatus(String orderCode);

    PaymentLatestResponse getLatestPaymentByContract(Long userId, Long projectId, Long contractId);

    PaymentLatestResponse getLatestPaymentByAddendum(Long userId, Long projectId, Long contractId);

}
