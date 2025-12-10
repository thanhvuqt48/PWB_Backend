package com.fpt.producerworkbench.service;

import com.fpt.producerworkbench.dto.request.AddBankAccountRequest;
import com.fpt.producerworkbench.dto.request.SendBankAccountOtpRequest;
import com.fpt.producerworkbench.dto.response.UserBankResponse;

import java.util.List;

public interface UserBankService {
    void sendBankAccountOtp(Long userId, SendBankAccountOtpRequest request);
    UserBankResponse addBankAccount(Long userId, AddBankAccountRequest request);
    List<UserBankResponse> getUserBanks(Long userId);
    UserBankResponse getUserBankById(Long userId, Long bankAccountId);
    void deleteUserBank(Long userId, Long bankAccountId);
}

