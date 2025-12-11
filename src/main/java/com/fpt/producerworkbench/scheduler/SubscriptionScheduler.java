package com.fpt.producerworkbench.scheduler;

import com.fpt.producerworkbench.common.NotificationType;
import com.fpt.producerworkbench.common.SubscriptionOrderType;
import com.fpt.producerworkbench.common.SubscriptionStatus;
import com.fpt.producerworkbench.common.TransactionStatus;
import com.fpt.producerworkbench.common.TransactionType;
import com.fpt.producerworkbench.common.UserRole;
import com.fpt.producerworkbench.configuration.PayosProperties;
import com.fpt.producerworkbench.configuration.SubscriptionProperties;
import com.fpt.producerworkbench.entity.SubscriptionOrder;
import com.fpt.producerworkbench.entity.Transaction;
import com.fpt.producerworkbench.entity.User;
import com.fpt.producerworkbench.entity.UserSubscription;
import com.fpt.producerworkbench.dto.event.NotificationEvent;
import com.fpt.producerworkbench.dto.request.SendNotificationRequest;
import com.fpt.producerworkbench.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.kafka.core.KafkaTemplate;
import com.fpt.producerworkbench.service.NotificationService;
import vn.payos.PayOS;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkRequest;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class SubscriptionScheduler {

    private final UserSubscriptionRepository userSubscriptionRepository;
    private final SubscriptionOrderRepository subscriptionOrderRepository;
    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final PayOS payOS;
    private final PayosProperties payosProperties;
    private final SubscriptionProperties subscriptionProperties;
    private final KafkaTemplate<String, NotificationEvent> kafkaTemplate;
    private final NotificationService notificationService;

    // Run every hour
    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void processRenewalsAndGrace() {
        LocalDateTime now = LocalDateTime.now();

        // 1) Start grace period and initiate renewal attempt
        List<UserSubscription> due = userSubscriptionRepository.findAutoRenewDue(now);
        for (UserSubscription s : due) {
            if (s.getGraceUntil() == null) {
                s.setGraceUntil(now.plusDays(subscriptionProperties.getGraceDays()));
                s.setStatus(SubscriptionStatus.ACTIVE);
                userSubscriptionRepository.save(s);

                tryInitiateRenewal(s);
            }
        }

        // 2) End grace period -> downgrade
        List<UserSubscription> graceEnded = userSubscriptionRepository.findGraceEnded(now);
        for (UserSubscription s : graceEnded) {
            s.setCurrent(false);
            s.setStatus(SubscriptionStatus.EXPIRED);
            s.setAutoRenewEnabled(false);
            userSubscriptionRepository.save(s);

            User u = s.getUser();
            if (u.getRole() == UserRole.PRODUCER) {
                u.setRole(UserRole.CUSTOMER);
                userRepository.save(u);
            }
        }
    }

    private void tryInitiateRenewal(UserSubscription s) {
        try {
            BigDecimal amount = s.getProPackage().getPrice();
            Transaction tx = Transaction.builder()
                    .user(s.getUser())
                    .amount(amount)
                    .type(TransactionType.SUBSCRIPTION)
                    .status(TransactionStatus.PENDING)
                    .build();
            Transaction saved = transactionRepository.save(tx);
            Long orderCode = com.fpt.producerworkbench.utils.OrderCodeGenerator.generate();
            saved.setTransactionCode(String.valueOf(orderCode));
            transactionRepository.save(saved);

            SubscriptionOrder order = SubscriptionOrder.builder()
                    .user(s.getUser())
                    .proPackage(s.getProPackage())
                    .orderType(SubscriptionOrderType.RENEW)
                    .transaction(saved)
                    .build();
            subscriptionOrderRepository.save(order);

            // Create link and ideally email the user
            CreatePaymentLinkRequest req = CreatePaymentLinkRequest.builder()
                    .orderCode(orderCode)
                    .amount(amount.setScale(0, RoundingMode.HALF_UP).longValueExact())
                    .description("PWB-PRO-RENEW-" + s.getUser().getId())
                    .returnUrl(payosProperties.getReturnUrl())
                    .cancelUrl(payosProperties.getCancelUrl())
                    .build();
            var res = payOS.paymentRequests().create(req);
            String checkoutUrl = res.getCheckoutUrl();
            log.info("Created renewal payment link for user {}: {}", s.getUser().getId(), checkoutUrl);

            // Publish Kafka notification for Thymeleaf template rendering
            try {
                NotificationEvent event = NotificationEvent.builder()
                        .subject(subscriptionProperties.getRenewalEmailSubject())
                        .recipient(s.getUser().getEmail())
                        .templateCode("subscription-renewal-notice")
                        .param(new java.util.HashMap<>())
                        .build();
                event.getParam().put("recipient", s.getUser().getFullName() == null ? s.getUser().getEmail() : s.getUser().getFullName());
                event.getParam().put("checkoutUrl", checkoutUrl);
                event.getParam().put("graceDays", String.valueOf(subscriptionProperties.getGraceDays()));
                kafkaTemplate.send("notification-delivery", event);
            } catch (Exception ex) {
                log.warn("Failed to publish renewal email event for {}: {}", s.getUser().getEmail(), ex.getMessage());
            }

            // Gửi notification realtime cho người dùng
            try {
                if (s.getUser() != null && s.getUser().getId() != null) {
                    String actionUrl = "/proPackage";

                    notificationService.sendNotification(
                            SendNotificationRequest.builder()
                                    .userId(s.getUser().getId())
                                    .type(NotificationType.SYSTEM)
                                    .title("Yêu cầu gia hạn gói đăng ký")
                                    .message(String.format("Gói đăng ký của bạn sắp hết hạn. Bạn có %d ngày để gia hạn để tiếp tục sử dụng dịch vụ.",
                                            subscriptionProperties.getGraceDays()))
                                    .relatedEntityType(null)
                                    .relatedEntityId(null)
                                    .actionUrl(actionUrl)
                                    .build());
                }
            } catch (Exception ex) {
                log.error("Gặp lỗi khi gửi notification realtime cho người dùng về gia hạn gói đăng ký: {}", ex.getMessage());
            }
        } catch (Exception e) {
            log.error("Failed to initiate renewal for subscription {}", s.getId(), e);
        }
    }
}


