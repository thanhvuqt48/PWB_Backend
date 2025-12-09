package com.fpt.producerworkbench.service;

import com.fpt.producerworkbench.common.WithdrawalStatus;
import com.fpt.producerworkbench.dto.request.RejectWithdrawalRequest;
import com.fpt.producerworkbench.dto.request.WithdrawalRequest;
import com.fpt.producerworkbench.dto.response.BalanceResponse;
import com.fpt.producerworkbench.dto.response.WithdrawalResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.Date;

public interface WithdrawalService {
    BalanceResponse getUserBalance(Long userId);

    WithdrawalResponse createWithdrawal(Long userId, WithdrawalRequest request);

    Page<WithdrawalResponse> getUserWithdrawals(Long userId, Pageable pageable);

    WithdrawalResponse getWithdrawalById(Long withdrawalId, Long userId);

    Page<WithdrawalResponse> getAllWithdrawals(Pageable pageable);

    WithdrawalResponse approveWithdrawal(Long withdrawalId);

    WithdrawalResponse rejectWithdrawal(Long withdrawalId, RejectWithdrawalRequest request);

    Page<WithdrawalResponse> searchUserWithdrawals(Long userId, String keyword, WithdrawalStatus status,
                                                   BigDecimal minAmount, BigDecimal maxAmount,
                                                   Date fromDate, Date toDate, Pageable pageable);

    Page<WithdrawalResponse> searchAllWithdrawals(String keyword, WithdrawalStatus status, Long userId,
                                                  BigDecimal minAmount, BigDecimal maxAmount,
                                                  Date fromDate, Date toDate, Pageable pageable);
}

