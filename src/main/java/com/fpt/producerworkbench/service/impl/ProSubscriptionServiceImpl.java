package com.fpt.producerworkbench.service.impl;

import com.fpt.producerworkbench.common.SubscriptionOrderType;
import com.fpt.producerworkbench.common.SubscriptionStatus;
import com.fpt.producerworkbench.common.TransactionStatus;
import com.fpt.producerworkbench.common.TransactionType;
import com.fpt.producerworkbench.common.UserRole;
import com.fpt.producerworkbench.configuration.PayosProperties;
import com.fpt.producerworkbench.dto.request.SubscriptionPurchaseRequest;
import com.fpt.producerworkbench.dto.request.SubscriptionUpgradeRequest;
import com.fpt.producerworkbench.dto.response.SubscriptionActionResponse;
import com.fpt.producerworkbench.dto.response.SubscriptionStatusResponse;
import com.fpt.producerworkbench.entity.*;
import com.fpt.producerworkbench.exception.AppException;
import com.fpt.producerworkbench.exception.ErrorCode;
import com.fpt.producerworkbench.repository.*;
import com.fpt.producerworkbench.service.ProSubscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.payos.PayOS;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkRequest;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkResponse;
import vn.payos.model.webhooks.WebhookData;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProSubscriptionServiceImpl implements ProSubscriptionService {

    private final PayOS payOS;
    private final PayosProperties payosProperties;
    private final UserRepository userRepository;
    private final ProPackageRepository proPackageRepository;
    private final UserSubscriptionRepository userSubscriptionRepository;
    private final SubscriptionOrderRepository subscriptionOrderRepository;
    private final TransactionRepository transactionRepository;

    @Override
    @Transactional
    public SubscriptionActionResponse purchase(Long userId, SubscriptionPurchaseRequest request) {
        User user = userRepository.findById(userId).orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        var existing = userSubscriptionRepository.findCurrentByUserId(userId);
        if (existing.isPresent()) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }

        ProPackage plan = proPackageRepository.findById(request.getProPackageId())
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND));

        BigDecimal amount = plan.getPrice();
        return createPaymentAndOrder(user, plan, SubscriptionOrderType.NEW, amount,
                request.getReturnUrl(), request.getCancelUrl());
    }

    @Override
    @Transactional
    public SubscriptionActionResponse upgrade(Long userId, SubscriptionUpgradeRequest request) {
        User user = userRepository.findById(userId).orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        UserSubscription current = userSubscriptionRepository.findCurrentByUserId(userId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND));

        ProPackage newPlan = proPackageRepository.findById(request.getNewProPackageId())
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND));

        if (current.getProPackage().getId().equals(newPlan.getId())) {
            throw new AppException(ErrorCode.INVALID_PARAMETER_FORMAT);
        }

        BigDecimal credit = calculateProrationCredit(current);
        BigDecimal amount = newPlan.getPrice().subtract(credit);
        if (amount.signum() < 0) amount = BigDecimal.ZERO;

        return createPaymentAndOrder(user, newPlan, SubscriptionOrderType.UPGRADE, amount,
                request.getReturnUrl(), request.getCancelUrl());
    }

    @Override
    @Transactional
    public void cancelAutoRenew(Long userId) {
        UserSubscription current = userSubscriptionRepository.findCurrentByUserId(userId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND));
        current.setAutoRenewEnabled(false);
        current.setCancelledAt(LocalDateTime.now());
        userSubscriptionRepository.save(current);
    }

    @Override
    @Transactional
    public void reactivateAutoRenew(Long userId) {
        UserSubscription current = userSubscriptionRepository.findCurrentByUserId(userId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND));
        current.setAutoRenewEnabled(true);
        current.setCancelledAt(null);
        userSubscriptionRepository.save(current);
    }

    @Override
    @Transactional(readOnly = true)
    public SubscriptionStatusResponse getStatus(Long userId) {
        var opt = userSubscriptionRepository.findCurrentByUserId(userId);
        if (opt.isEmpty()) {
            return SubscriptionStatusResponse.builder()
                    .status(SubscriptionStatus.EXPIRED)
                    .autoRenewEnabled(false)
                    .build();
        }
        var s = opt.get();
        return SubscriptionStatusResponse.builder()
                .status(s.getStatus())
                .planName(s.getProPackage().getName())
                .endDate(s.getEndDate())
                .autoRenewEnabled(s.isAutoRenewEnabled())
                .graceUntil(s.getGraceUntil())
                .build();
    }

    @Override
    @Transactional
    public void handleWebhook(String body) {
        try {
            if (body == null || body.trim().isEmpty()) {
                throw new AppException(ErrorCode.INVALID_PARAMETER_FORMAT);
            }
            WebhookData verified;
            try {
                verified = payOS.webhooks().verify(body);
            } catch (Exception sigEx) {
                String msg = sigEx.getMessage() != null ? sigEx.getMessage().toLowerCase() : "";
                if (msg.contains("signature") || msg.contains("checksum")) {
                    log.warn("Webhook bị giả mạo hoặc chữ ký sai: {}", sigEx.getMessage());
                    throw new AppException(ErrorCode.INVALID_SIGNATURE);
                }
                throw sigEx;
            }
            long orderCodeLong = verified.getOrderCode();
            if (orderCodeLong <= 0) throw new AppException(ErrorCode.INVALID_PARAMETER_FORMAT);

            String payosStatusCode = verified.getCode();
            Transaction tx = transactionRepository.findByTransactionCode(String.valueOf(orderCodeLong))
                    .orElseThrow(() -> new AppException(ErrorCode.TRANSACTION_NOT_FOUND));

            if (!TransactionStatus.PENDING.equals(tx.getStatus())) {
                return;
            }

            if ("00".equals(payosStatusCode)) {
                tx.setStatus(TransactionStatus.SUCCESSFUL);
                processSuccessfulSubscriptionOrder(tx);
            } else {
                tx.setStatus(TransactionStatus.FAILED);
            }
            transactionRepository.save(tx);
        } catch (Exception e) {
            log.error("Subscription webhook error", e);
            throw e;
        }
    }

    private SubscriptionActionResponse createPaymentAndOrder(
            User user,
            ProPackage plan,
            SubscriptionOrderType type,
            BigDecimal amount,
            String returnUrl,
            String cancelUrl
    ) {
        Transaction tx = Transaction.builder()
                .user(user)
                .amount(amount)
                .type(TransactionType.SUBSCRIPTION)
                .status(TransactionStatus.PENDING)
                .build();
        Transaction saved = transactionRepository.save(tx);
        Long orderCode = com.fpt.producerworkbench.utils.OrderCodeGenerator.generate();
        saved.setTransactionCode(String.valueOf(orderCode));
        transactionRepository.save(saved);

        SubscriptionOrder order = SubscriptionOrder.builder()
                .user(user)
                .proPackage(plan)
                .orderType(type)
                .transaction(saved)
                .build();
        subscriptionOrderRepository.save(order);

        String ru = returnUrl != null ? returnUrl : payosProperties.getReturnUrl();
        String cu = cancelUrl != null ? cancelUrl : payosProperties.getCancelUrl();

        try {
            CreatePaymentLinkRequest req = CreatePaymentLinkRequest.builder()
                    .orderCode(orderCode)
                    .amount(toPayOSAmountVND(amount))
                    .description("PWB-PRO-" + user.getId())
                    .returnUrl(ru)
                    .cancelUrl(cu)
                    .build();

            CreatePaymentLinkResponse res = payOS.paymentRequests().create(req);
            return SubscriptionActionResponse.builder()
                    .paymentUrl(res.getCheckoutUrl())
                    .orderCode(String.valueOf(orderCode))
                    .amount(amount)
                    .status("PENDING")
                    .build();
        } catch (Exception e) {
            log.error("Failed to create PayOS link for subscription", e);
            throw new AppException(ErrorCode.PAYMENT_LINK_CREATION_FAILED);
        }
    }

    private void processSuccessfulSubscriptionOrder(Transaction tx) {
        SubscriptionOrder order = subscriptionOrderRepository.findByTransaction(tx)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND));

        LocalDateTime now = LocalDateTime.now();

        if (order.getOrderType() == SubscriptionOrderType.NEW) {
            // End any existing current subscriptions
            userSubscriptionRepository.findCurrentByUserId(order.getUser().getId()).ifPresent(s -> {
                s.setCurrent(false);
                userSubscriptionRepository.save(s);
            });

            UserSubscription s = UserSubscription.builder()
                    .user(order.getUser())
                    .proPackage(order.getProPackage())
                    .startDate(now)
                    .endDate(now.plusMonths(order.getProPackage().getDurationMonths()))
                    .status(SubscriptionStatus.ACTIVE)
                    .autoRenewEnabled(true)
                    .current(true)
                    .transaction(tx)
                    .build();
            userSubscriptionRepository.save(s);
            // upgrade role to PRODUCER
            User u = order.getUser();
            if (u.getRole() != UserRole.PRODUCER) {
                u.setRole(UserRole.PRODUCER);
                userRepository.save(u);
            }
        } else if (order.getOrderType() == SubscriptionOrderType.UPGRADE) {
            UserSubscription current = userSubscriptionRepository.findCurrentByUserId(order.getUser().getId())
                    .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND));
            current.setProPackage(order.getProPackage());
            current.setStartDate(now);
            current.setEndDate(now.plusMonths(order.getProPackage().getDurationMonths()));
            current.setStatus(SubscriptionStatus.ACTIVE);
            current.setTransaction(tx);
            userSubscriptionRepository.save(current);
        } else if (order.getOrderType() == SubscriptionOrderType.RENEW) {
            UserSubscription current = userSubscriptionRepository.findCurrentByUserId(order.getUser().getId())
                    .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND));
            current.setEndDate(current.getEndDate().plusMonths(order.getProPackage().getDurationMonths()));
            current.setGraceUntil(null);
            current.setStatus(SubscriptionStatus.ACTIVE);
            current.setTransaction(tx);
            userSubscriptionRepository.save(current);
        }
    }

    private BigDecimal calculateProrationCredit(UserSubscription current) {
        LocalDateTime now = LocalDateTime.now();
        if (now.isAfter(current.getEndDate())) {
            return BigDecimal.ZERO;
        }
        long totalDays = java.time.Duration.between(current.getStartDate(), current.getEndDate()).toDays();
        long remainingDays = java.time.Duration.between(now, current.getEndDate()).toDays();
        if (remainingDays <= 0 || totalDays <= 0) return BigDecimal.ZERO;
        BigDecimal fraction = new BigDecimal(remainingDays).divide(new BigDecimal(totalDays), 6, RoundingMode.HALF_UP);
        return current.getProPackage().getPrice().multiply(fraction).setScale(0, RoundingMode.HALF_UP);
    }

    private long toPayOSAmountVND(BigDecimal amount) {
        return amount.setScale(0, RoundingMode.HALF_UP).longValueExact();
    }
}


