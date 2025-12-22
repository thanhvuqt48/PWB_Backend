package com.fpt.producerworkbench.service.impl;

import com.fpt.producerworkbench.common.TransactionStatus;
import com.fpt.producerworkbench.dto.response.AdminDashboardResponse;
import com.fpt.producerworkbench.dto.response.ProPackageTimeSeriesResponse;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
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

    @Override
    @Transactional(readOnly = true)
    public ProPackageTimeSeriesResponse getProPackageTimeSeriesStats(Integer year, String period, String currentEmail) {
        if (currentEmail == null || currentEmail.isBlank()) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        
        User user = userRepository.findByEmail(currentEmail)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        
        if (!"ADMIN".equals(user.getRole().name())) {
            throw new AppException(ErrorCode.ACCESS_DENIED, "Chỉ Admin mới có quyền xem thống kê");
        }

        if (year == null) {
            year = LocalDate.now().getYear();
        }

        List<Object[]> rawData;
        List<String> timeLabels;
        
        if ("year".equalsIgnoreCase(period)) {
            // Thống kê theo năm (5 năm gần nhất)
            LocalDateTime start = LocalDateTime.of(year - 4, 1, 1, 0, 0);
            LocalDateTime end = LocalDateTime.of(year, 12, 31, 23, 59, 59);
            rawData = subscriptionOrderRepository.countPackageSalesByYearWithRevenue(start, end);
            timeLabels = generateYearLabels(start.getYear(), end.getYear());
        } else {
            // Thống kê theo tháng (mặc định)
            rawData = subscriptionOrderRepository.countPackageSalesByMonthWithRevenue(year);
            timeLabels = generateMonthLabels(year);
        }

        return buildTimeSeriesResponse(rawData, timeLabels, period);
    }

    private List<String> generateMonthLabels(int year) {
        List<String> labels = new ArrayList<>();
        for (int month = 1; month <= 12; month++) {
            labels.add(String.format("%d-%02d", year, month));
        }
        return labels;
    }

    private List<String> generateYearLabels(int startYear, int endYear) {
        List<String> labels = new ArrayList<>();
        for (int y = startYear; y <= endYear; y++) {
            labels.add(String.valueOf(y));
        }
        return labels;
    }

    private ProPackageTimeSeriesResponse buildTimeSeriesResponse(
            List<Object[]> rawData, 
            List<String> timeLabels, 
            String period) {
        
        // Map dữ liệu: key = timeLabel, value = Map<packageName, PackageStats>
        Map<String, Map<String, PackageStats>> dataMap = new HashMap<>();
        Set<String> packageNames = new HashSet<>();
        
        for (Object[] row : rawData) {
            String timeLabel = (String) row[0];
            String packageName = (String) row[1];
            Long count = ((Number) row[2]).longValue();
            
            // Xử lý revenue từ row[3]
            BigDecimal revenue = BigDecimal.ZERO;
            if (row[3] != null) {
                if (row[3] instanceof BigDecimal) {
                    revenue = (BigDecimal) row[3];
                } else if (row[3] instanceof Double) {
                    revenue = BigDecimal.valueOf((Double) row[3]);
                } else if (row[3] instanceof Long) {
                    revenue = BigDecimal.valueOf((Long) row[3]);
                } else if (row[3] instanceof Number) {
                    revenue = BigDecimal.valueOf(((Number) row[3]).doubleValue());
                }
            }
            
            packageNames.add(packageName);
            dataMap.computeIfAbsent(timeLabel, k -> new HashMap<>())
                    .put(packageName, new PackageStats(count, revenue));
        }
        
        // Tạo danh sách package data
        List<ProPackageTimeSeriesResponse.PackageTimeSeriesData> packageDataList = new ArrayList<>();
        for (String packageName : packageNames.stream().sorted().collect(Collectors.toList())) {
            List<Long> soldCounts = new ArrayList<>();
            List<BigDecimal> revenues = new ArrayList<>();
            
            for (String timeLabel : timeLabels) {
                PackageStats stats = dataMap.getOrDefault(timeLabel, Collections.emptyMap())
                        .getOrDefault(packageName, new PackageStats(0L, BigDecimal.ZERO));
                soldCounts.add(stats.count);
                revenues.add(stats.revenue);
            }
            
            packageDataList.add(ProPackageTimeSeriesResponse.PackageTimeSeriesData.builder()
                    .packageName(packageName)
                    .soldCounts(soldCounts)
                    .revenues(revenues)
                    .build());
        }
        
        // Tính tổng số gói đã bán và tổng doanh thu theo từng thời điểm
        List<Long> totalSold = new ArrayList<>();
        List<BigDecimal> totalRevenue = new ArrayList<>();
        
        for (String timeLabel : timeLabels) {
            Map<String, PackageStats> packageStats = dataMap.getOrDefault(timeLabel, Collections.emptyMap());
            
            long totalCount = packageStats.values().stream()
                    .mapToLong(s -> s.count)
                    .sum();
            
            BigDecimal totalRev = packageStats.values().stream()
                    .map(s -> s.revenue)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            totalSold.add(totalCount);
            totalRevenue.add(totalRev);
        }
        
        // Tính tổng doanh thu của cả khoảng thời gian
        BigDecimal totalRevenueForPeriod = totalRevenue.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        return ProPackageTimeSeriesResponse.builder()
                .timeLabels(timeLabels)
                .packageData(packageDataList)
                .totalSold(totalSold)
                .totalRevenue(totalRevenue)
                .totalRevenueForPeriod(totalRevenueForPeriod)
                .build();
    }
    
    // Helper class để lưu count và revenue
    private static class PackageStats {
        Long count;
        BigDecimal revenue;
        
        PackageStats(Long count, BigDecimal revenue) {
            this.count = count;
            this.revenue = revenue;
        }
    }
}