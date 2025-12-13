package com.fpt.producerworkbench.scheduler;

import com.fpt.producerworkbench.common.TaxStatus;
import com.fpt.producerworkbench.common.TerminatedBy;
import com.fpt.producerworkbench.common.TransactionType;
import com.fpt.producerworkbench.entity.Contract;
import com.fpt.producerworkbench.entity.TaxRecord;
import com.fpt.producerworkbench.entity.User;
import com.fpt.producerworkbench.repository.TaxRecordRepository;
import com.fpt.producerworkbench.service.impl.ContractTerminationServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Scheduler xử lý hoàn thuế tự động
 * Chạy vào ngày 20 hàng tháng để hoàn thuế cho các hợp đồng chấm dứt sau ngày 20
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TaxRefundScheduler {
    
    private final TaxRecordRepository taxRecordRepository;
    private final ContractTerminationServiceImpl terminationService;
    
    /**
     * Chạy vào 10:00 sáng ngày 20 hàng tháng
     * Xử lý hoàn thuế cho các hợp đồng đã chấm dứt
     */
    @Scheduled(cron = "0 0 10 20 * ?")
    @Transactional
    public void processMonthlyTaxRefunds() {
        LocalDate today = LocalDate.now();
        
        log.info("=== Starting tax refund processing for date: {} ===", today);
        
        // Lấy các TaxRecord cần hoàn thuế
        List<TaxRecord> pendingRefunds = taxRecordRepository
                .findPendingRefunds(TaxStatus.WAITING_REFUND, today);
        
        if (pendingRefunds.isEmpty()) {
            log.info("No pending tax refunds found for today");
            return;
        }
        
        log.info("Found {} pending tax refunds to process", pendingRefunds.size());
        
        int successCount = 0;
        int failCount = 0;
        
        for (TaxRecord record : pendingRefunds) {
            try {
                processSecondPayment(record);
                successCount++;
            } catch (Exception e) {
                log.error("Failed to process tax refund for record: " + record.getId(), e);
                failCount++;
            }
        }
        
        log.info("=== Tax refund processing completed. Success: {}, Failed: {} ===",
                successCount, failCount);
    }
    
    /**
     * Xử lý thanh toán lần 2 (hoàn thuế)
     */
    private void processSecondPayment(TaxRecord record) {
        log.info("Processing second payment (tax refund) for TaxRecord: {}", record.getId());
        
        Contract contract = record.getContract();
        User owner = contract.getProject().getCreator();
        User client = contract.getProject().getClient();
        
        var refundAmount = record.getRefundedTax();
        
        if (refundAmount == null || refundAmount.signum() <= 0) {
            log.warn("No tax refund amount for record: {}", record.getId());
            record.setStatus(TaxStatus.REFUNDED);
            record.setRefundCompletedDate(LocalDate.now());
            taxRecordRepository.save(record);
            return;
        }
        
        // Xác định người nhận hoàn thuế
        User recipient;
        TransactionType transactionType;
        String description;
        
        if (record.getTerminatedBy() == TerminatedBy.CLIENT) {
            // CLIENT chấm dứt → OWNER nhận hoàn thuế
            recipient = owner;
            transactionType = TransactionType.TAX_REFUND;
            description = "Tax refund (round 2) - Client terminated contract";
        } else {
            // OWNER chấm dứt → CLIENT nhận hoàn thuế
            recipient = client;
            transactionType = TransactionType.TAX_REFUND;
            description = "Tax refund (round 2) - Owner terminated contract";
        }
        
        // Cộng tiền hoàn vào balance
        terminationService.updateUserBalancePublic(
                recipient,
                refundAmount,
                transactionType,
                contract,
                description
        );
        
        // Cập nhật trạng thái TaxRecord
        record.setStatus(TaxStatus.REFUNDED);
        record.setRefundCompletedDate(LocalDate.now());
        taxRecordRepository.save(record);
        
        log.info("Tax refund processed successfully. Amount: {}, Recipient: {} ({})",
                refundAmount, recipient.getFullName(), recipient.getId());
        
        // TODO: Gửi notification cho user
    }
}


