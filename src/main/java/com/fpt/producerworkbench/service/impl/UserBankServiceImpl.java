package com.fpt.producerworkbench.service.impl;

import com.fpt.producerworkbench.dto.event.NotificationEvent;
import com.fpt.producerworkbench.dto.request.AddBankAccountRequest;
import com.fpt.producerworkbench.dto.request.SendBankAccountOtpRequest;
import com.fpt.producerworkbench.dto.response.UserBankResponse;
import com.fpt.producerworkbench.entity.Bank;
import com.fpt.producerworkbench.entity.User;
import com.fpt.producerworkbench.entity.UserBank;
import com.fpt.producerworkbench.exception.AppException;
import com.fpt.producerworkbench.exception.ErrorCode;
import com.fpt.producerworkbench.mapper.UserBankMapper;
import com.fpt.producerworkbench.repository.BankRepository;
import com.fpt.producerworkbench.repository.UserBankRepository;
import com.fpt.producerworkbench.repository.UserRepository;
import com.fpt.producerworkbench.service.OtpService;
import com.fpt.producerworkbench.service.UserBankService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

import static com.fpt.producerworkbench.utils.SecurityUtils.generateOtp;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserBankServiceImpl implements UserBankService {

    private final UserRepository userRepository;
    private final BankRepository bankRepository;
    private final UserBankRepository userBankRepository;
    private final UserBankMapper userBankMapper;
    private final OtpService otpService;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Override
    public void sendBankAccountOtp(Long userId, SendBankAccountOtpRequest request) {
        log.info("Gửi OTP để thêm ngân hàng cho user: {}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        Bank bank = bankRepository.findById(request.getBankId())
                .orElseThrow(() -> new AppException(ErrorCode.INVALID_BANK_CODE));

        String otp = generateOtp();
        String otpKey = "bank_account_otp:" + userId + ":" + request.getBankId() + ":" + request.getAccountNumber();
        
        otpService.saveOtp(otpKey, otp);

        NotificationEvent event = NotificationEvent.builder()
                .channel("EMAIL")
                .recipient(user.getEmail())
                .templateCode("otp-register-vi")
                .subject("Mã OTP xác thực thông tin ngân hàng")
                .param(new HashMap<>())
                .build();

        event.getParam().put("recipient", user.getEmail());
        event.getParam().put("otp", otp);
        event.getParam().put("validMinutes", "5");
        event.getParam().put("bankName", bank.getName());
        event.getParam().put("accountNumber", maskAccountNumber(request.getAccountNumber()));

        kafkaTemplate.send("notification-delivery", event);

        log.info("Đã gửi OTP cho user {} để thêm ngân hàng {}", userId, bank.getName());
    }

    @Override
    @Transactional
    public UserBankResponse addBankAccount(Long userId, AddBankAccountRequest request) {
        log.info("Thêm ngân hàng cho user: {}, bankId: {}", userId, request.getBankId());

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        Bank bank = bankRepository.findById(request.getBankId())
                .orElseThrow(() -> new AppException(ErrorCode.INVALID_BANK_CODE));

        String otpKey = "bank_account_otp:" + userId + ":" + request.getBankId() + ":" + request.getAccountNumber();
        String storedOtp = otpService.getOtp(otpKey);

        if (storedOtp == null || !storedOtp.equals(request.getOtp())) {
            throw new AppException(ErrorCode.OTP_INVALID);
        }

        UserBank userBank = UserBank.builder()
                .user(user)
                .bank(bank)
                .accountNumber(request.getAccountNumber())
                .accountHolderName(request.getAccountHolderName())
                .isVerified(true)
                .build();

        UserBank saved = userBankRepository.save(userBank);
        otpService.deleteOtp(otpKey);

        log.info("Đã thêm ngân hàng thành công cho user: {}, bankAccountId: {}", userId, saved.getId());

        return userBankMapper.toUserBankResponse(saved);
    }

    @Override
    public List<UserBankResponse> getUserBanks(Long userId) {
        log.info("Lấy danh sách ngân hàng của user: {}", userId);

        List<UserBank> userBanks = userBankRepository.findByUserId(userId);
        return userBanks.stream()
                .map(userBankMapper::toUserBankResponse)
                .collect(Collectors.toList());
    }

    @Override
    public UserBankResponse getUserBankById(Long userId, Long bankAccountId) {
        log.info("Lấy thông tin ngân hàng: {} của user: {}", bankAccountId, userId);

        UserBank userBank = userBankRepository.findByUserIdAndId(userId, bankAccountId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_BANK_NOT_FOUND));

        return userBankMapper.toUserBankResponse(userBank);
    }

    @Override
    @Transactional
    public void deleteUserBank(Long userId, Long bankAccountId) {
        log.info("Xóa ngân hàng: {} của user: {}", bankAccountId, userId);

        UserBank userBank = userBankRepository.findByUserIdAndId(userId, bankAccountId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_BANK_NOT_FOUND));

        userBankRepository.delete(userBank);

        log.info("Đã xóa ngân hàng thành công");
    }

    private String maskAccountNumber(String accountNumber) {
        if (accountNumber == null || accountNumber.length() <= 4) {
            return accountNumber;
        }
        return "****" + accountNumber.substring(accountNumber.length() - 4);
    }
}

