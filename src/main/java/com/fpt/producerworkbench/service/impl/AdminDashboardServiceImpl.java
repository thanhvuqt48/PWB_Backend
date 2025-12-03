package com.fpt.producerworkbench.service.impl;

import com.fpt.producerworkbench.common.TransactionStatus;
import com.fpt.producerworkbench.dto.response.AdminDashboardResponse;
import com.fpt.producerworkbench.dto.response.RevenueStat;
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
import java.util.List;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminDashboardServiceImpl implements AdminDashboardService {

    private final TransactionRepository transactionRepository;
    private final SubscriptionOrderRepository subscriptionOrderRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public AdminDashboardResponse getDashboardStats(LocalDate fromDate, LocalDate toDate, String groupBy, String currentEmail) {
        if (currentEmail == null || currentEmail.isBlank()) throw new AppException(ErrorCode.UNAUTHENTICATED);
        User user = userRepository.findByEmail(currentEmail)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        if (!"ADMIN".equals(user.getRole().name())) {
            throw new AppException(ErrorCode.ACCESS_DENIED, "Chỉ Admin mới có quyền xem thống kê");
        }

        LocalDateTime start = (fromDate != null) ? fromDate.atStartOfDay() : LocalDate.now().minusDays(30).atStartOfDay();
        LocalDateTime end = (toDate != null) ? toDate.atTime(LocalTime.MAX) : LocalDate.now().atTime(LocalTime.MAX);

        var totalRevenue = transactionRepository.sumTotalRevenue(TransactionStatus.SUCCESSFUL, start, end);
        var totalPackagesSold = transactionRepository.countByStatusAndDateRange(TransactionStatus.SUCCESSFUL, start, end);
        var packageStats = subscriptionOrderRepository.countPackageSales(start, end);

        List<Object[]> rawData;

        if ("month".equalsIgnoreCase(groupBy)) {
            rawData = transactionRepository.getRevenueByMonth(TransactionStatus.SUCCESSFUL, start, end);
        } else if ("year".equalsIgnoreCase(groupBy)) {
            rawData = transactionRepository.getRevenueByYear(TransactionStatus.SUCCESSFUL, start, end);
        } else {
            rawData = transactionRepository.getRevenueByDay(TransactionStatus.SUCCESSFUL, start, end);
        }

        // Tự map từ Object[] sang DTO RevenueStat
        List<RevenueStat> revenueStats = mapToRevenueStats(rawData);

        return AdminDashboardResponse.builder()
                .totalRevenue(totalRevenue)
                .totalPackagesSold(totalPackagesSold)
                .revenueStats(revenueStats) // Set list DTO vào response
                .packageSalesStats(packageStats)
                .build();
    }

    private List<RevenueStat> mapToRevenueStats(List<Object[]> rawData) {
        if (rawData == null || rawData.isEmpty()) {
            return new ArrayList<>();
        }

        return rawData.stream().map(obj -> {
            String label = (String) obj[0]; // Cột 1 là String (Date Format)

            // Xử lý an toàn cho cột Amount (Cột 2) tránh lỗi Null hoặc sai kiểu số
            BigDecimal amount = BigDecimal.ZERO;
            if (obj[1] != null) {
                if (obj[1] instanceof BigDecimal) {
                    amount = (BigDecimal) obj[1];
                } else if (obj[1] instanceof Double) {
                    amount = BigDecimal.valueOf((Double) obj[1]);
                } else if (obj[1] instanceof Long) {
                    amount = BigDecimal.valueOf((Long) obj[1]);
                }
            }

            return new RevenueStat(label, amount);
        }).collect(Collectors.toList());
    }
}