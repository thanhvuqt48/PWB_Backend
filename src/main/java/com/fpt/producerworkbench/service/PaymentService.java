package com.fpt.producerworkbench.service;

import com.fpt.producerworkbench.dto.request.PaymentRequest;
import com.fpt.producerworkbench.dto.response.PaymentResponse;
import org.springframework.transaction.TransactionStatus;

public interface PaymentService {

    PaymentResponse createPayment(Long userId, Long projectId, Long contractId, PaymentRequest paymentRequest);

    void handlePaymentWebhook(String rawBody);

}
