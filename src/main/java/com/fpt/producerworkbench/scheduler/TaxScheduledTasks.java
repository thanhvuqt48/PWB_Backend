package com.fpt.producerworkbench.scheduler;

import com.fpt.producerworkbench.common.TaxPeriodType;
import com.fpt.producerworkbench.entity.User;
import com.fpt.producerworkbench.repository.UserRepository;
import com.fpt.producerworkbench.service.TaxSummaryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * Scheduled tasks for automatic tax summary generation
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TaxScheduledTasks {
    
    private final TaxSummaryService taxSummaryService;
    private final UserRepository userRepository;
    
    /**
     * Chạy vào 00:00 ngày 1 hàng tháng
     * Tạo summary tháng trước cho tất cả users
     */
    @Scheduled(cron = "0 0 0 1 * ?")
    public void generateMonthlySummaries() {
        LocalDate now = LocalDate.now();
        int year = now.getYear();
        int lastMonth = now.minusMonths(1).getMonthValue();
        
        log.info("Starting monthly tax summary generation for {}/{}", lastMonth, year);
        
        List<User> activeUsers = userRepository.findAll(); // TODO: add findAllActive method
        int successCount = 0;
        int failCount = 0;
        
        for (User user : activeUsers) {
            try {
                taxSummaryService.calculateUserTaxSummary(
                        user.getId(),
                        TaxPeriodType.MONTHLY,
                        year,
                        lastMonth
                );
                successCount++;
            } catch (Exception e) {
                log.error("Failed to generate summary for user: " + user.getId(), e);
                failCount++;
            }
        }
        
        log.info("Monthly summary generation completed. Success: {}, Failed: {}",
                successCount, failCount);
    }
    
    /**
     * Chạy vào 00:00 ngày 1 của tháng đầu quý
     * Tạo summary quý trước cho tất cả users
     */
    @Scheduled(cron = "0 0 0 1 1,4,7,10 ?")
    public void generateQuarterlySummaries() {
        LocalDate now = LocalDate.now();
        int year = now.getYear();
        int currentMonth = now.getMonthValue();
        int lastQuarter = ((currentMonth - 1) / 3);
        if (lastQuarter == 0) {
            lastQuarter = 4;
            year--;
        }
        
        log.info("Starting quarterly tax summary generation for Q{}/{}", lastQuarter, year);
        
        List<User> activeUsers = userRepository.findAll();
        
        for (User user : activeUsers) {
            try {
                taxSummaryService.calculateUserTaxSummary(
                        user.getId(),
                        TaxPeriodType.QUARTERLY,
                        year,
                        lastQuarter
                );
            } catch (Exception e) {
                log.error("Failed to generate quarterly summary for user: " + user.getId(), e);
            }
        }
        
        log.info("Quarterly summary generation completed");
    }
    
    /**
     * Chạy vào 00:00 ngày 1/1 hàng năm
     * Tạo summary năm trước cho tất cả users
     */
    @Scheduled(cron = "0 0 0 1 1 ?")
    public void generateAnnualSummaries() {
        int lastYear = LocalDate.now().getYear() - 1;
        
        log.info("Starting annual tax summary generation for {}", lastYear);
        
        List<User> activeUsers = userRepository.findAll();
        
        for (User user : activeUsers) {
            try {
                taxSummaryService.calculateUserTaxSummary(
                        user.getId(),
                        TaxPeriodType.YEARLY,
                        lastYear,
                        null
                );
            } catch (Exception e) {
                log.error("Failed to generate annual summary for user: " + user.getId(), e);
            }
        }
        
        log.info("Annual summary generation completed");
    }
}


