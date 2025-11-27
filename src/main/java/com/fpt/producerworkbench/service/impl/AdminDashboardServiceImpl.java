package com.fpt.producerworkbench.service.impl;

import com.fpt.producerworkbench.common.TransactionStatus;
import com.fpt.producerworkbench.dto.response.AdminDashboardResponse;
import com.fpt.producerworkbench.entity.User;
import com.fpt.producerworkbench.exception.AppException;
import com.fpt.producerworkbench.exception.ErrorCode;
import com.fpt.producerworkbench.repository.SubscriptionOrderRepository;
import com.fpt.producerworkbench.repository.TransactionRepository;
import com.fpt.producerworkbench.repository.UserRepository;
import com.fpt.producerworkbench.service.AdminDashboardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminDashboardServiceImpl implements AdminDashboardService {

    private final TransactionRepository transactionRepository;
    private final SubscriptionOrderRepository subscriptionOrderRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public AdminDashboardResponse getDashboardStats(LocalDate fromDate, LocalDate toDate, String currentEmail) {
        if (currentEmail == null || currentEmail.isBlank()) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        User currentUser = userRepository.findByEmail(currentEmail)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        if (!"ADMIN".equals(currentUser.getRole().name())) {
            throw new AppException(ErrorCode.ACCESS_DENIED, "Chỉ Admin mới có quyền xem thống kê");
        }

        LocalDateTime start = (fromDate != null)
                ? fromDate.atStartOfDay()
                : LocalDate.now().minusDays(30).atStartOfDay();

        LocalDateTime end = (toDate != null)
                ? toDate.atTime(LocalTime.MAX)
                : LocalDate.now().atTime(LocalTime.MAX);

        log.info("Admin {} đang xem thống kê từ {} đến {}", currentEmail, start, end);

        var totalRevenue = transactionRepository.sumTotalRevenue(TransactionStatus.SUCCESSFUL);

        var totalTx = transactionRepository.countByStatus(TransactionStatus.SUCCESSFUL);

        var totalSubs = subscriptionOrderRepository.count();

        var subRevenue = subscriptionOrderRepository.sumSubscriptionRevenue();

        var dailyStats = transactionRepository.getDailyRevenue(TransactionStatus.SUCCESSFUL, start, end);

        var packageStats = subscriptionOrderRepository.countPackageSales();

        return AdminDashboardResponse.builder()
                .totalRevenue(totalRevenue)
                .totalSuccessTransactions(totalTx)
                .totalSubscriptionOrders(totalSubs)
                .dailyRevenueStats(dailyStats)
                .packageSalesStats(packageStats)
                .build();
    }
}