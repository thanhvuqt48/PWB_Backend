package com.fpt.producerworkbench.service;

import com.fpt.producerworkbench.dto.request.PaymentRequest;
import com.fpt.producerworkbench.dto.response.PaymentResponse;
import com.fpt.producerworkbench.dto.response.PaymentStatusResponse;
import com.fpt.producerworkbench.dto.response.PaymentLatestResponse;

public interface PaymentService {

    PaymentResponse createPayment(Long userId, Long projectId, Long contractId, PaymentRequest paymentRequest);

    PaymentResponse createAddendumPayment(Long userId, Long projectId, Long contractId, PaymentRequest paymentRequest);

    void handlePaymentWebhook(String rawBody);

    PaymentStatusResponse getPaymentStatus(String orderCode);

    PaymentLatestResponse getLatestPaymentByContract(Long userId, Long projectId, Long contractId);

    PaymentLatestResponse getLatestPaymentByAddendum(Long userId, Long projectId, Long contractId);

}
