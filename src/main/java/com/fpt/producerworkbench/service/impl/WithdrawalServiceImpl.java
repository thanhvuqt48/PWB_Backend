package com.fpt.producerworkbench.service.impl;

import com.fpt.producerworkbench.common.NotificationType;
import com.fpt.producerworkbench.common.WithdrawalStatus;
import com.fpt.producerworkbench.configuration.VietQrProperties;
import com.fpt.producerworkbench.dto.event.NotificationEvent;
import com.fpt.producerworkbench.dto.request.RejectWithdrawalRequest;
import com.fpt.producerworkbench.dto.request.SendNotificationRequest;
import com.fpt.producerworkbench.dto.request.WithdrawalRequest;
import com.fpt.producerworkbench.dto.response.BalanceResponse;
import com.fpt.producerworkbench.dto.response.WithdrawalResponse;
import com.fpt.producerworkbench.repository.spec.WithdrawalSpecification;
import org.springframework.data.jpa.domain.Specification;
import com.fpt.producerworkbench.dto.vietqr.VietQrGenerateRequest;
import com.fpt.producerworkbench.dto.vietqr.VietQrGenerateResponse;
import com.fpt.producerworkbench.entity.Bank;
import com.fpt.producerworkbench.entity.User;
import com.fpt.producerworkbench.entity.Withdrawal;
import com.fpt.producerworkbench.exception.AppException;
import com.fpt.producerworkbench.exception.ErrorCode;
import com.fpt.producerworkbench.mapper.WithdrawalMapper;
import com.fpt.producerworkbench.repository.BankRepository;
import com.fpt.producerworkbench.repository.UserRepository;
import com.fpt.producerworkbench.repository.WithdrawalRepository;
import com.fpt.producerworkbench.repository.http_client.VietQrClient;
import com.fpt.producerworkbench.service.NotificationService;
import com.fpt.producerworkbench.service.WithdrawalService;
import com.fpt.producerworkbench.utils.OrderCodeGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class WithdrawalServiceImpl implements WithdrawalService {

    private final UserRepository userRepository;
    private final BankRepository bankRepository;
    private final WithdrawalRepository withdrawalRepository;
    private final WithdrawalMapper withdrawalMapper;
    private final VietQrClient vietQrClient;
    private final VietQrProperties vietQrProperties;
    private final KafkaTemplate<String, NotificationEvent> kafkaTemplate;
    private final NotificationService notificationService;

    private static final BigDecimal MIN_WITHDRAWAL_AMOUNT = new BigDecimal("50000"); // 50,000 VND
    private static final BigDecimal MAX_WITHDRAWAL_AMOUNT = new BigDecimal("100000000"); // 100,000,000 VND
    private static final String NOTIFICATION_TOPIC = "notification-delivery";

    @Override
    @Transactional
    public WithdrawalResponse createWithdrawal(Long userId, WithdrawalRequest request) {
        log.info("Tạo yêu cầu rút tiền cho user: {}, số tiền: {}", userId, request.getAmount());

        validateAmount(request.getAmount());

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        if (user.getBalance() == null || user.getBalance().compareTo(request.getAmount()) < 0) {
            throw new AppException(ErrorCode.INSUFFICIENT_BALANCE);
        }

        Bank bank = bankRepository.findById(request.getBankId())
                .orElseThrow(() -> new AppException(ErrorCode.INVALID_BANK_CODE));

        Long orderCode = OrderCodeGenerator.generate();
        String withdrawalCode = String.valueOf(orderCode);

        String qrDataURL = null;
        if (bank.getBin() != null && !bank.getBin().trim().isEmpty()) {
            try {
                VietQrGenerateRequest qrRequest = VietQrGenerateRequest.builder()
                        .accountNo(request.getAccountNumber())
                        .accountName(request.getAccountHolderName())
                        .acqId(bank.getBin())
                        .amount(request.getAmount().longValue())
                        .addInfo("Rut tien tu Producer Workbench - " + withdrawalCode)
                        .format("text")
                        .template("print")
                        .build();

                VietQrGenerateResponse qrResponse = vietQrClient.generateQrCode(
                        qrRequest,
                        vietQrProperties.getApiKey(),
                        vietQrProperties.getClientId());

                if ("00".equals(qrResponse.getCode()) && qrResponse.getData() != null) {
                    qrDataURL = qrResponse.getData().getQrDataURL();
                    log.info("Tạo VietQR code thành công cho withdrawal: {}", withdrawalCode);
                } else {
                    log.warn("Tạo VietQR code thất bại. Code: {}, Desc: {}",
                            qrResponse.getCode(), qrResponse.getDesc());
                }
            } catch (Exception e) {
                log.error("Lỗi khi gọi VietQR API: {}", e.getMessage(), e);
            }
        } else {
            log.warn("Bank {} không có BIN, bỏ qua việc tạo VietQR code", bank.getCode());
        }

        Withdrawal withdrawal = Withdrawal.builder()
                .user(user)
                .amount(request.getAmount())
                .bank(bank)
                .accountNumber(request.getAccountNumber())
                .accountHolderName(request.getAccountHolderName())
                .status(WithdrawalStatus.PENDING)
                .withdrawalCode(withdrawalCode)
                .qrDataURL(qrDataURL)
                .build();

        BigDecimal newBalance = user.getBalance().subtract(request.getAmount());
        user.setBalance(newBalance);

        Withdrawal saved = withdrawalRepository.save(withdrawal);
        userRepository.save(user);

        log.info("Tạo yêu cầu rút tiền thành công. Withdrawal ID: {}, Code: {}",
                saved.getId(), saved.getWithdrawalCode());

        // Gửi email và notification realtime cho người dùng
        sendWithdrawalCreatedNotification(saved, user, newBalance);

        return withdrawalMapper.toWithdrawalResponse(saved, newBalance);
    }

    @Override
    public Page<WithdrawalResponse> getUserWithdrawals(Long userId, Pageable pageable) {
        Page<Withdrawal> withdrawals = withdrawalRepository.findByUserId(userId, pageable);
        return withdrawals.map(w -> {
            BigDecimal balance = w.getUser().getBalance();
            return withdrawalMapper.toWithdrawalResponse(w, balance);
        });
    }

    @Override
    public WithdrawalResponse getWithdrawalById(Long withdrawalId, Long userId) {
        Withdrawal withdrawal = withdrawalRepository.findById(withdrawalId)
                .orElseThrow(() -> new AppException(ErrorCode.WITHDRAWAL_NOT_FOUND));

        if (!withdrawal.getUser().getId().equals(userId)) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }

        BigDecimal balance = withdrawal.getUser().getBalance();
        return withdrawalMapper.toWithdrawalResponse(withdrawal, balance);
    }

    @Override
    public Page<WithdrawalResponse> getAllWithdrawals(Pageable pageable) {
        Page<Withdrawal> withdrawals = withdrawalRepository.findAll(pageable);
        return withdrawals.map(w -> {
            BigDecimal balance = w.getUser().getBalance();
            return withdrawalMapper.toWithdrawalResponse(w, balance);
        });
    }

    @Override
    @Transactional
    public WithdrawalResponse approveWithdrawal(Long withdrawalId) {
        log.info("Admin chấp nhận chuyển tiền cho withdrawal: {}", withdrawalId);

        Withdrawal withdrawal = withdrawalRepository.findById(withdrawalId)
                .orElseThrow(() -> new AppException(ErrorCode.WITHDRAWAL_NOT_FOUND));

        if (withdrawal.getStatus() != WithdrawalStatus.PENDING) {
            throw new AppException(ErrorCode.INVALID_WITHDRAWAL_STATUS,
                    "Chỉ có thể chấp nhận yêu cầu rút tiền đang ở trạng thái PENDING");
        }

        withdrawal.setStatus(WithdrawalStatus.COMPLETED);
        Withdrawal saved = withdrawalRepository.save(withdrawal);

        log.info("Admin đã chấp nhận chuyển tiền thành công. Withdrawal ID: {}, Code: {}",
                saved.getId(), saved.getWithdrawalCode());

        // Gửi email và notification realtime cho người dùng
        sendWithdrawalApprovedNotification(saved, saved.getUser());

        BigDecimal balance = saved.getUser().getBalance();
        return withdrawalMapper.toWithdrawalResponse(saved, balance);
    }

    @Override
    @Transactional
    public WithdrawalResponse rejectWithdrawal(Long withdrawalId, RejectWithdrawalRequest request) {
        log.info("Admin từ chối chuyển tiền cho withdrawal: {}, lý do: {}",
                withdrawalId, request.getRejectionReason());

        Withdrawal withdrawal = withdrawalRepository.findById(withdrawalId)
                .orElseThrow(() -> new AppException(ErrorCode.WITHDRAWAL_NOT_FOUND));

        if (withdrawal.getStatus() != WithdrawalStatus.PENDING) {
            throw new AppException(ErrorCode.INVALID_WITHDRAWAL_STATUS,
                    "Chỉ có thể từ chối yêu cầu rút tiền đang ở trạng thái PENDING");
        }

        withdrawal.setStatus(WithdrawalStatus.REJECTED);
        withdrawal.setRejectionReason(request.getRejectionReason());

        User user = withdrawal.getUser();
        BigDecimal currentBalance = user.getBalance() != null ? user.getBalance() : BigDecimal.ZERO;
        BigDecimal newBalance = currentBalance.add(withdrawal.getAmount());
        user.setBalance(newBalance);

        Withdrawal saved = withdrawalRepository.save(withdrawal);
        userRepository.save(user);

        log.info("Admin đã từ chối chuyển tiền. Withdrawal ID: {}, Code: {}. Đã hoàn lại {} VND cho user {}",
                saved.getId(), saved.getWithdrawalCode(), withdrawal.getAmount(), user.getId());

        sendWithdrawalRejectedNotification(saved, user, newBalance, request.getRejectionReason());

        return withdrawalMapper.toWithdrawalResponse(saved, newBalance);
    }

    @Override
    public Page<WithdrawalResponse> searchUserWithdrawals(Long userId, String keyword, WithdrawalStatus status,
                                                          BigDecimal minAmount, BigDecimal maxAmount,
                                                          Date fromDate, Date toDate, Pageable pageable) {
        log.info("Tìm kiếm withdrawal của user: {}, keyword: {}, status: {}", userId, keyword, status);

        Specification<Withdrawal> spec = WithdrawalSpecification.hasUserId(userId);

        if (keyword != null && !keyword.trim().isEmpty()) {
            spec = spec.and(WithdrawalSpecification.hasKeyword(keyword.trim()));
        }
        if (status != null) {
            spec = spec.and(WithdrawalSpecification.hasStatus(status));
        }
        if (minAmount != null || maxAmount != null) {
            spec = spec.and(WithdrawalSpecification.hasAmountBetween(minAmount, maxAmount));
        }
        if (fromDate != null || toDate != null) {
            spec = spec.and(WithdrawalSpecification.hasCreatedAtBetween(fromDate, toDate));
        }

        Page<Withdrawal> withdrawals = withdrawalRepository.findAll(spec, pageable);
        return withdrawals.map(w -> {
            BigDecimal balance = w.getUser().getBalance();
            return withdrawalMapper.toWithdrawalResponse(w, balance);
        });
    }

    @Override
    public Page<WithdrawalResponse> searchAllWithdrawals(String keyword, WithdrawalStatus status, Long userId,
                                                         BigDecimal minAmount, BigDecimal maxAmount,
                                                         Date fromDate, Date toDate, Pageable pageable) {
        log.info("Admin tìm kiếm tất cả withdrawal, keyword: {}, status: {}, userId: {}", keyword, status, userId);

        Specification<Withdrawal> spec = null;

        if (keyword != null && !keyword.trim().isEmpty()) {
            spec = combineSpec(spec, WithdrawalSpecification.hasKeyword(keyword.trim()));
        }
        if (status != null) {
            spec = combineSpec(spec, WithdrawalSpecification.hasStatus(status));
        }
        if (userId != null) {
            spec = combineSpec(spec, WithdrawalSpecification.hasUserId(userId));
        }
        if (minAmount != null || maxAmount != null) {
            spec = combineSpec(spec, WithdrawalSpecification.hasAmountBetween(minAmount, maxAmount));
        }
        if (fromDate != null || toDate != null) {
            spec = combineSpec(spec, WithdrawalSpecification.hasCreatedAtBetween(fromDate, toDate));
        }

        Page<Withdrawal> withdrawals = withdrawalRepository.findAll(spec, pageable);
        return withdrawals.map(w -> {
            BigDecimal balance = w.getUser().getBalance();
            return withdrawalMapper.toWithdrawalResponse(w, balance);
        });
    }

    @Override
    public BalanceResponse getUserBalance(Long userId) {
        log.info("Lấy balance của user: {}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        BigDecimal balance = user.getBalance() != null ? user.getBalance() : BigDecimal.ZERO;

        return BalanceResponse.builder()
                .balance(balance)
                .build();
    }

    private void sendWithdrawalCreatedNotification(Withdrawal withdrawal, User user, BigDecimal newBalance) {
        if (user.getEmail() == null || user.getEmail().isBlank()) {
            log.warn("Không thể gửi thông báo tạo yêu cầu rút tiền vì user {} không có email", user.getId());
            return;
        }

        try {
            // Gửi email
            Map<String, Object> params = new HashMap<>();
            params.put("recipientName", user.getFullName() != null ? user.getFullName() : user.getEmail());
            params.put("withdrawalCode", withdrawal.getWithdrawalCode());
            params.put("amount", withdrawal.getAmount().toString());
            params.put("accountNumber", withdrawal.getAccountNumber());
            params.put("accountHolderName", withdrawal.getAccountHolderName());
            params.put("bankName", withdrawal.getBank().getName());
            params.put("newBalance", newBalance.toString());

            NotificationEvent event = NotificationEvent.builder()
                    .recipient(user.getEmail())
                    .subject("Yêu cầu rút tiền đã được tạo - " + withdrawal.getWithdrawalCode())
                    .templateCode("withdrawal-created-notification")
                    .param(params)
                    .build();

            kafkaTemplate.send(NOTIFICATION_TOPIC, event);
            log.info("Đã gửi email thông báo tạo yêu cầu rút tiền qua Kafka: userId={}, withdrawalId={}",
                    user.getId(), withdrawal.getId());

            // Gửi notification realtime
            String actionUrl = String.format("/withdrawals");

            notificationService.sendNotification(
                    SendNotificationRequest.builder()
                            .userId(user.getId())
                            .type(NotificationType.SYSTEM)
                            .title("Yêu cầu rút tiền đã được tạo")
                            .message(String.format("Yêu cầu rút tiền của bạn với mã %s đã được tạo thành công. Số tiền: %s VND. Yêu cầu đang được xử lý.",
                                    withdrawal.getWithdrawalCode(),
                                    withdrawal.getAmount().toString()))
                            .relatedEntityType(null)
                            .relatedEntityId(null)
                            .actionUrl(actionUrl)
                            .build());
        } catch (Exception e) {
            log.error("Lỗi khi gửi thông báo tạo yêu cầu rút tiền: userId={}, withdrawalId={}",
                    user.getId(), withdrawal.getId(), e);
        }
    }

    private void sendWithdrawalApprovedNotification(Withdrawal withdrawal, User user) {
        if (user.getEmail() == null || user.getEmail().isBlank()) {
            log.warn("Không thể gửi thông báo chấp nhận yêu cầu rút tiền vì user {} không có email", user.getId());
            return;
        }

        try {
            // Gửi email
            Map<String, Object> params = new HashMap<>();
            params.put("recipientName", user.getFullName() != null ? user.getFullName() : user.getEmail());
            params.put("withdrawalCode", withdrawal.getWithdrawalCode());
            params.put("amount", withdrawal.getAmount().toString());
            params.put("accountNumber", withdrawal.getAccountNumber());
            params.put("accountHolderName", withdrawal.getAccountHolderName());
            params.put("bankName", withdrawal.getBank().getName());

            NotificationEvent event = NotificationEvent.builder()
                    .recipient(user.getEmail())
                    .subject("Yêu cầu rút tiền đã được chấp nhận - " + withdrawal.getWithdrawalCode())
                    .templateCode("withdrawal-approved-notification")
                    .param(params)
                    .build();

            kafkaTemplate.send(NOTIFICATION_TOPIC, event);
            log.info("Đã gửi email thông báo chấp nhận yêu cầu rút tiền qua Kafka: userId={}, withdrawalId={}",
                    user.getId(), withdrawal.getId());

            // Gửi notification realtime
            String actionUrl = String.format("/withdrawals");

            notificationService.sendNotification(
                    SendNotificationRequest.builder()
                            .userId(user.getId())
                            .type(NotificationType.SYSTEM)
                            .title("Yêu cầu rút tiền đã được chấp nhận")
                            .message(String.format("Yêu cầu rút tiền của bạn với mã %s đã được chấp nhận. Số tiền %s VND sẽ được chuyển đến tài khoản của bạn trong thời gian sớm nhất.",
                                    withdrawal.getWithdrawalCode(),
                                    withdrawal.getAmount().toString()))
                            .relatedEntityType(null)
                            .relatedEntityId(null)
                            .actionUrl(actionUrl)
                            .build());
        } catch (Exception e) {
            log.error("Lỗi khi gửi thông báo chấp nhận yêu cầu rút tiền: userId={}, withdrawalId={}",
                    user.getId(), withdrawal.getId(), e);
        }
    }

    private void sendWithdrawalRejectedNotification(Withdrawal withdrawal, User user, BigDecimal newBalance, String rejectionReason) {
        if (user.getEmail() == null || user.getEmail().isBlank()) {
            log.warn("Không thể gửi thông báo từ chối yêu cầu rút tiền vì user {} không có email", user.getId());
            return;
        }

        try {
            // Gửi email
            Map<String, Object> params = new HashMap<>();
            params.put("recipientName", user.getFullName() != null ? user.getFullName() : user.getEmail());
            params.put("withdrawalCode", withdrawal.getWithdrawalCode());
            params.put("amount", withdrawal.getAmount().toString());
            params.put("rejectionReason", rejectionReason != null ? rejectionReason : "Không có lý do cụ thể");
            params.put("newBalance", newBalance.toString());

            NotificationEvent event = NotificationEvent.builder()
                    .recipient(user.getEmail())
                    .subject("Yêu cầu rút tiền đã bị từ chối - " + withdrawal.getWithdrawalCode())
                    .templateCode("withdrawal-rejected-notification")
                    .param(params)
                    .build();

            kafkaTemplate.send(NOTIFICATION_TOPIC, event);
            log.info("Đã gửi email thông báo từ chối yêu cầu rút tiền qua Kafka: userId={}, withdrawalId={}",
                    user.getId(), withdrawal.getId());

            // Gửi notification realtime
            String actionUrl = String.format("/withdrawals");

            notificationService.sendNotification(
                    SendNotificationRequest.builder()
                            .userId(user.getId())
                            .type(NotificationType.SYSTEM)
                            .title("Yêu cầu rút tiền đã bị từ chối")
                            .message(String.format("Yêu cầu rút tiền của bạn với mã %s đã bị từ chối. Số tiền %s VND đã được hoàn lại vào tài khoản của bạn.%s",
                                    withdrawal.getWithdrawalCode(),
                                    withdrawal.getAmount().toString(),
                                    rejectionReason != null ? " Lý do: " + rejectionReason : ""))
                            .relatedEntityType(null)
                            .relatedEntityId(null)
                            .actionUrl(actionUrl)
                            .build());
        } catch (Exception e) {
            log.error("Lỗi khi gửi thông báo từ chối yêu cầu rút tiền: userId={}, withdrawalId={}",
                    user.getId(), withdrawal.getId(), e);
        }
    }

    private Specification<Withdrawal> combineSpec(Specification<Withdrawal> existing,
                                                  Specification<Withdrawal> additional) {
        return existing == null ? additional : existing.and(additional);
    }

    private void validateAmount(BigDecimal amount) {
        if (amount.compareTo(MIN_WITHDRAWAL_AMOUNT) < 0) {
            throw new AppException(ErrorCode.AMOUNT_TOO_SMALL);
        }
        if (amount.compareTo(MAX_WITHDRAWAL_AMOUNT) > 0) {
            throw new AppException(ErrorCode.AMOUNT_TOO_LARGE);
        }
    }
}
