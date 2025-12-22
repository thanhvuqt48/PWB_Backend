package com.fpt.producerworkbench.controller;

import com.fpt.producerworkbench.dto.response.AdminDashboardResponse;
import com.fpt.producerworkbench.dto.response.ProPackageTimeSeriesResponse;
import com.fpt.producerworkbench.service.AdminDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.security.Principal;

@RestController
@RequestMapping("/api/admin/stats")
@RequiredArgsConstructor
public class AdminDashboardController {

    private final AdminDashboardService adminDashboardService;

    @GetMapping
    public ResponseEntity<AdminDashboardResponse> getDashboardStats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(defaultValue = "day") String groupBy,
            Principal principal
    ) {
        return ResponseEntity.ok(adminDashboardService.getDashboardStats(fromDate, toDate, groupBy, principal.getName()));
    }

    /**
     * Lấy thống kê gói Pro theo chuỗi thời gian (tháng hoặc năm)
     * Dùng để vẽ biểu đồ với trục hoành là thời gian và trục tung là số lượng đã bán
     * 
     * @param year Năm cần thống kê (mặc định là năm hiện tại)
     * @param period Loại thống kê: "month" (theo tháng) hoặc "year" (theo năm), mặc định là "month"
     * @param principal Thông tin user đăng nhập
     * @return Thống kê gói Pro theo chuỗi thời gian
     */
    @GetMapping("/pro-package-time-series")
    public ResponseEntity<ProPackageTimeSeriesResponse> getProPackageTimeSeriesStats(
            @RequestParam(required = false) Integer year,
            @RequestParam(defaultValue = "month") String period,
            Principal principal
    ) {
        return ResponseEntity.ok(adminDashboardService.getProPackageTimeSeriesStats(year, period, principal.getName()));
    }
}