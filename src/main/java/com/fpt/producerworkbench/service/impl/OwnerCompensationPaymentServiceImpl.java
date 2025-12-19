package com.fpt.producerworkbench.service.impl;

import com.fpt.producerworkbench.common.PaymentStatus;
import com.fpt.producerworkbench.entity.Contract;
import com.fpt.producerworkbench.entity.OwnerCompensationPayment;
import com.fpt.producerworkbench.entity.User;
import com.fpt.producerworkbench.exception.AppException;
import com.fpt.producerworkbench.exception.ErrorCode;
import com.fpt.producerworkbench.repository.ContractRepository;
import com.fpt.producerworkbench.repository.OwnerCompensationPaymentRepository;
import com.fpt.producerworkbench.repository.UserRepository;
import com.fpt.producerworkbench.service.ContractTerminationService;
import com.fpt.producerworkbench.service.OwnerCompensationPaymentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.payos.PayOS;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkRequest;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkResponse;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Implementation of Owner Compensation Payment Service
 * Sử dụng PayOS để Owner chuyển tiền đền bù Team
 */
@Service
@Slf4j
public class OwnerCompensationPaymentServiceImpl implements OwnerCompensationPaymentService {
    
    private final OwnerCompensationPaymentRepository paymentRepository;
    private final ContractRepository contractRepository;
    private final UserRepository userRepository;
    private final PayOS payOS;
    private final ContractTerminationService terminationService;
    
    @Value("${payos.return-url:http://localhost:3000/payment/success}")
    private String returnUrl;
    
    @Value("${payos.cancel-url:http://localhost:3000/payment/cancel}")
    private String cancelUrl;
    
    // Constructor với @Lazy để phá circular dependency
    @Autowired
    public OwnerCompensationPaymentServiceImpl(
            OwnerCompensationPaymentRepository paymentRepository,
            ContractRepository contractRepository,
            UserRepository userRepository,
            PayOS payOS,
            @Lazy ContractTerminationService terminationService
    ) {
        this.paymentRepository = paymentRepository;
        this.contractRepository = contractRepository;
        this.userRepository = userRepository;
        this.payOS = payOS;
        this.terminationService = terminationService;
    }
    
    @Override
    @Transactional
    public OwnerCompensationPayment createPaymentOrder(
            Long contractId,
            Long ownerId,
            BigDecimal amount,
            String description
    ) {
        log.info("Creating owner compensation payment order. Contract: {}, Owner: {}, Amount: {}",
                contractId, ownerId, amount);
        
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new AppException(ErrorCode.CONTRACT_NOT_FOUND));
        
        User owner = userRepository.findById(ownerId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        
        // Tạo order code unique
        Long orderCode = com.fpt.producerworkbench.utils.OrderCodeGenerator.generate();
        String orderCodeStr = "OCP-" + orderCode;
        
        // Tạo OwnerCompensationPayment entity
        OwnerCompensationPayment payment = OwnerCompensationPayment.builder()
                .contract(contract)
                .owner(owner)
                .totalAmount(amount)
                .paymentOrderCode(orderCodeStr)
                .status(PaymentStatus.PENDING)
                .expiredAt(LocalDateTime.now().plusHours(24))
                .description(description)
                .build();
        
        payment = paymentRepository.save(payment);
        
        // Tạo PayOS payment link
        try {
            long amountInVND = amount.setScale(0, java.math.RoundingMode.HALF_UP).longValue();
            
            // PayOS yêu cầu description tối đa 25 ký tự
            String shortDescription = "Comp #" + contractId;
            if (shortDescription.length() > 25) {
                // Nếu vẫn dài, chỉ lấy 19 ký tự đầu (bao gồm contractId)
                shortDescription = shortDescription.substring(0, 25);
            }
            
            CreatePaymentLinkRequest request = CreatePaymentLinkRequest.builder()
                    .orderCode(orderCode)
                    .amount((long) amountInVND)
                    .description(shortDescription)
                    .returnUrl(returnUrl + "?orderCode=" + orderCodeStr)
                    .cancelUrl(cancelUrl + "?orderCode=" + orderCodeStr)
                    .build();
            
            CreatePaymentLinkResponse response = payOS.paymentRequests().create(request);
            String checkoutUrl = response.getCheckoutUrl();
            
            payment.setPaymentUrl(checkoutUrl);
            payment.setPaymentOrderId(response.getPaymentLinkId());
            paymentRepository.save(payment);
            
            log.info("Created PayOS payment link successfully. URL: {}", checkoutUrl);
            
            return payment;
            
        } catch (Exception e) {
            log.error("Failed to create PayOS payment link for owner compensation", e);
            payment.setStatus(com.fpt.producerworkbench.common.PaymentStatus.FAILED);
            payment.setFailureReason("Failed to create PayOS payment link: " + e.getMessage());
            paymentRepository.save(payment);
            throw new AppException(ErrorCode.PAYMENT_LINK_CREATION_FAILED);
        }
    }
    
    @Override
    @Transactional
    public void handlePaymentWebhook(String orderCode, String status) {
        log.info("Handling payment webhook. OrderCode: {}, Status: {}", orderCode, status);
        
        if (!"PAID".equals(status) && !"SUCCESS".equals(status)) {
            log.warn("Payment not successful: {}", status);
            
            // Cập nhật status nếu failed/cancelled
            paymentRepository.findByPaymentOrderCode(orderCode)
                    .ifPresent(payment -> {
                        if ("CANCELLED".equals(status)) {
                            payment.setStatus(com.fpt.producerworkbench.common.PaymentStatus.EXPIRED);
                            payment.setFailureReason("Payment cancelled by user");
                        } else {
                            payment.setStatus(com.fpt.producerworkbench.common.PaymentStatus.FAILED);
                            payment.setFailureReason("Payment failed with status: " + status);
                        }
                        paymentRepository.save(payment);
                    });
            return;
        }
        
        // Payment successful → Trigger contract termination service
        terminationService.handleOwnerCompensationWebhook(orderCode, status);
    }
}


