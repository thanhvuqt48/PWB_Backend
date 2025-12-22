package com.fpt.producerworkbench.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProPackageTimeSeriesResponse {
    /**
     * Nhãn thời gian (ví dụ: "2024-01", "2024-02", ... cho tháng hoặc "2024", "2025", ... cho năm)
     * Dùng làm trục hoành cho biểu đồ
     */
    private List<String> timeLabels;
    
    /**
     * Dữ liệu thống kê theo từng gói Pro
     * Mỗi package có một danh sách số lượng đã bán tương ứng với timeLabels
     */
    private List<PackageTimeSeriesData> packageData;
    
    /**
     * Tổng số gói đã bán theo từng thời điểm
     * Tương ứng với timeLabels, dùng làm trục tung cho biểu đồ tổng
     */
    private List<Long> totalSold;
    
    /**
     * Tổng doanh thu theo từng thời điểm (tương ứng với timeLabels)
     * Dùng làm trục tung cho biểu đồ doanh thu tổng
     */
    private List<BigDecimal> totalRevenue;
    
    /**
     * Tổng doanh thu của cả khoảng thời gian (tổng của tất cả các thời điểm)
     * Ví dụ: Tổng doanh thu cả năm 2024, hoặc tổng doanh thu 5 năm (2020-2024)
     */
    private BigDecimal totalRevenueForPeriod;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PackageTimeSeriesData {
        /**
         * Tên gói Pro
         */
        private String packageName;
        
        /**
         * Số lượng đã bán theo từng thời điểm (tương ứng với timeLabels)
         * Dùng làm trục tung cho biểu đồ từng gói
         */
        private List<Long> soldCounts;
        
        /**
         * Doanh thu theo từng thời điểm (tương ứng với timeLabels)
         * Dùng làm trục tung cho biểu đồ doanh thu từng gói
         */
        private List<BigDecimal> revenues;
    }
}

