package com.fpt.producerworkbench.service.impl;

import com.fpt.producerworkbench.common.TaxPeriodType;
import com.fpt.producerworkbench.common.TaxSummaryStatus;
import com.fpt.producerworkbench.constant.*;
import com.fpt.producerworkbench.entity.TaxPayoutRecord;
import com.fpt.producerworkbench.entity.User;
import com.fpt.producerworkbench.entity.UserTaxSummary;
import com.fpt.producerworkbench.exception.AppException;
import com.fpt.producerworkbench.exception.ErrorCode;
import com.fpt.producerworkbench.repository.TaxPayoutRecordRepository;
import com.fpt.producerworkbench.repository.UserRepository;
import com.fpt.producerworkbench.repository.UserTaxSummaryRepository;
import com.fpt.producerworkbench.service.TaxSummaryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class TaxSummaryServiceImpl implements TaxSummaryService {
    
    private final UserRepository userRepository;
    private final TaxPayoutRecordRepository taxPayoutRecordRepository;
    private final UserTaxSummaryRepository userTaxSummaryRepository;
    
    @Override
    @Transactional
    public UserTaxSummary calculateUserTaxSummary(
            Long userId,
            TaxPeriodType periodType,
            int year,
            Integer monthOrQuarter
    ) {
        log.info("Calculating tax summary for user: {}, period: {} {}/{}",
                userId, periodType, monthOrQuarter, year);
        
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        
        // Kiểm tra user đã xác thực CCCD chưa
        if (!Boolean.TRUE.equals(user.getIsVerified()) || user.getCccdNumber() == null) {
            throw new AppException(ErrorCode.RESOURCE_NOT_FOUND,
                    "User identity verification not found. Please verify CCCD first.");
        }
        
        // Xác định khoảng thời gian
        LocalDate startDate, endDate;
        if (periodType == TaxPeriodType.MONTHLY) {
            startDate = LocalDate.of(year, monthOrQuarter, 1);
            endDate = startDate.plusMonths(1).minusDays(1);
        } else if (periodType == TaxPeriodType.QUARTERLY) {
            int startMonth = (monthOrQuarter - 1) * 3 + 1;
            startDate = LocalDate.of(year, startMonth, 1);
            endDate = startDate.plusMonths(3).minusDays(1);
        } else { // YEARLY
            startDate = LocalDate.of(year, 1, 1);
            endDate = LocalDate.of(year, 12, 31);
        }
        
        // Lấy tất cả payout records trong kỳ
        List<TaxPayoutRecord> records = taxPayoutRecordRepository
                .findByUserIdAndPayoutDateBetween(userId, startDate, endDate);
        
        // Tính tổng
        BigDecimal totalGross = BigDecimal.ZERO;
        BigDecimal totalTax = BigDecimal.ZERO;
        BigDecimal incomeFromMilestone = BigDecimal.ZERO;
        BigDecimal incomeFromTermination = BigDecimal.ZERO;
        BigDecimal incomeFromRefund = BigDecimal.ZERO;
        BigDecimal incomeFromOther = BigDecimal.ZERO;
        
        Set<Long> contractIds = new HashSet<>();
        int withdrawalCount = 0;
        
        for (TaxPayoutRecord record : records) {
            totalGross = totalGross.add(record.getGrossAmount());
            totalTax = totalTax.add(record.getTaxAmount());
            
            switch (record.getPayoutSource()) {
                case MILESTONE_PAYMENT:
                    incomeFromMilestone = incomeFromMilestone.add(record.getGrossAmount());
                    break;
                case TERMINATION_COMPENSATION:
                    incomeFromTermination = incomeFromTermination.add(record.getGrossAmount());
                    break;
                case TAX_REFUND:
                    incomeFromRefund = incomeFromRefund.add(record.getGrossAmount());
                    break;
                default:
                    incomeFromOther = incomeFromOther.add(record.getGrossAmount());
            }
            
            if (record.getContract() != null) {
                contractIds.add(record.getContract().getId());
            }
            
            if (record.getWithdrawalId() != null) {
                withdrawalCount++;
            }
        }
        
        // Tính thuế suất thực tế
        BigDecimal effectiveRate = BigDecimal.ZERO;
        if (totalGross.compareTo(BigDecimal.ZERO) > 0) {
            effectiveRate = totalTax.divide(totalGross, 4, RoundingMode.HALF_UP);
        }
        
        // Tạo hoặc cập nhật summary
        UserTaxSummary summary = UserTaxSummary.builder()
                .user(user)
                .userCccd(user.getCccdNumber())
                .userTaxCode(user.getTaxCode() != null ? user.getTaxCode() : user.getCccdNumber())
                .userFullName(user.getCccdFullName())
                .taxPeriodType(periodType)
                .taxPeriodYear(year)
                .taxPeriodMonth(periodType == TaxPeriodType.MONTHLY ? monthOrQuarter : null)
                .taxPeriodQuarter(periodType == TaxPeriodType.QUARTERLY ? monthOrQuarter : null)
                .periodStartDate(startDate)
                .periodEndDate(endDate)
                .totalGrossIncome(totalGross)
                .totalTaxableIncome(totalGross)
                .totalNonTaxableIncome(BigDecimal.ZERO)
                .incomeFromMilestone(incomeFromMilestone)
                .incomeFromTermination(incomeFromTermination)
                .incomeFromRefund(incomeFromRefund)
                .incomeFromOther(incomeFromOther)
                .totalTaxWithheld(totalTax)
                .totalTaxPaid(totalTax)
                .totalTaxRefunded(BigDecimal.ZERO)
                .totalTaxDue(BigDecimal.ZERO)
                .effectiveTaxRate(effectiveRate)
                .totalPayoutCount(records.size())
                .totalContractCount(contractIds.size())
                .totalWithdrawalCount(withdrawalCount)
                .status(TaxSummaryStatus.DRAFT)
                .build();
        
        return userTaxSummaryRepository.save(summary);
    }
    
    @Override
    @Transactional(readOnly = true)
    public Page<UserTaxSummary> getUserTaxReport(
            Integer year,
            Integer month,
            Pageable pageable
    ) {
        return userTaxSummaryRepository.findByYearAndMonth(year, month, pageable);
    }
}

