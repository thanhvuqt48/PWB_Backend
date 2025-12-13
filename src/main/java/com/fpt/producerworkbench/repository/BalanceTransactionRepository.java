package com.fpt.producerworkbench.repository;

import com.fpt.producerworkbench.common.TransactionType;
import com.fpt.producerworkbench.entity.BalanceTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface BalanceTransactionRepository extends JpaRepository<BalanceTransaction, Long> {
    
    List<BalanceTransaction> findByUserId(Long userId);
    
    Page<BalanceTransaction> findByUserId(Long userId, Pageable pageable);
    
    List<BalanceTransaction> findByContractId(Long contractId);
    
    List<BalanceTransaction> findByUserIdAndType(Long userId, TransactionType type);
    
    List<BalanceTransaction> findByUserIdAndCreatedAtBetween(
        Long userId, 
        LocalDateTime startDate, 
        LocalDateTime endDate
    );
}


