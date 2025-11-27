package com.fpt.producerworkbench.repository;

import com.fpt.producerworkbench.common.TransactionStatus;
import com.fpt.producerworkbench.dto.response.DailyRevenueStat;
import com.fpt.producerworkbench.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.data.repository.query.Param;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    Optional<Transaction> findByTransactionCode(String transactionCode);

    Optional<Transaction> findTopByRelatedContract_IdOrderByCreatedAtDesc(Long contractId);


    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t WHERE t.status = :status")
    BigDecimal sumTotalRevenue(@Param("status") TransactionStatus status);

    long countByStatus(TransactionStatus status);


    @Query("SELECT new com.fpt.producerworkbench.dto.response.DailyRevenueStat(CAST(t.createdAt AS LocalDate), SUM(t.amount)) " +
            "FROM Transaction t " +
            "WHERE t.status = :status AND t.createdAt BETWEEN :startDate AND :endDate " +
            "GROUP BY CAST(t.createdAt AS LocalDate) " +
            "ORDER BY CAST(t.createdAt AS LocalDate) ASC")
    List<DailyRevenueStat> getDailyRevenue(@Param("status") TransactionStatus status,
                                           @Param("startDate") LocalDateTime startDate,
                                           @Param("endDate") LocalDateTime endDate);
}
