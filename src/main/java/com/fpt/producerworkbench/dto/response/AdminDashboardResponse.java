package com.fpt.producerworkbench.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class AdminDashboardResponse {
    private BigDecimal totalRevenue;
    private Long totalSuccessTransactions;
    private Long totalSubscriptionOrders;


    private List<DailyRevenueStat> dailyRevenueStats;

    private List<PackageSalesStat> packageSalesStats;
}