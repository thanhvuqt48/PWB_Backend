package com.fpt.producerworkbench.repository;

import com.fpt.producerworkbench.common.TransactionStatus;
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

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t " +
            "WHERE t.status = :status AND t.createdAt BETWEEN :startDate AND :endDate")
    BigDecimal sumTotalRevenue(@Param("status") TransactionStatus status,
                               @Param("startDate") LocalDateTime startDate,
                               @Param("endDate") LocalDateTime endDate);

    @Query("SELECT COUNT(t) FROM Transaction t " +
            "WHERE t.status = :status AND t.createdAt BETWEEN :startDate AND :endDate")
    long countByStatusAndDateRange(@Param("status") TransactionStatus status,
                                   @Param("startDate") LocalDateTime startDate,
                                   @Param("endDate") LocalDateTime endDate);

    // 1. By DAY: Trả về List<Object[]>
    @Query("SELECT FUNCTION('DATE_FORMAT', t.createdAt, '%Y-%m-%d'), SUM(t.amount) " +
            "FROM Transaction t " +
            "WHERE t.status = :status AND t.createdAt BETWEEN :startDate AND :endDate " +
            "GROUP BY FUNCTION('DATE_FORMAT', t.createdAt, '%Y-%m-%d') " +
            "ORDER BY FUNCTION('DATE_FORMAT', t.createdAt, '%Y-%m-%d') ASC")
    List<Object[]> getRevenueByDay(@Param("status") TransactionStatus status,
                                   @Param("startDate") LocalDateTime startDate,
                                   @Param("endDate") LocalDateTime endDate);

    // 2. By MONTH: Trả về List<Object[]>
    @Query("SELECT FUNCTION('DATE_FORMAT', t.createdAt, '%Y-%m'), SUM(t.amount) " +
            "FROM Transaction t " +
            "WHERE t.status = :status AND t.createdAt BETWEEN :startDate AND :endDate " +
            "GROUP BY FUNCTION('DATE_FORMAT', t.createdAt, '%Y-%m') " +
            "ORDER BY FUNCTION('DATE_FORMAT', t.createdAt, '%Y-%m') ASC")
    List<Object[]> getRevenueByMonth(@Param("status") TransactionStatus status,
                                     @Param("startDate") LocalDateTime startDate,
                                     @Param("endDate") LocalDateTime endDate);

    // 3. By YEAR: Trả về List<Object[]>
    @Query("SELECT FUNCTION('DATE_FORMAT', t.createdAt, '%Y'), SUM(t.amount) " +
            "FROM Transaction t " +
            "WHERE t.status = :status AND t.createdAt BETWEEN :startDate AND :endDate " +
            "GROUP BY FUNCTION('DATE_FORMAT', t.createdAt, '%Y') " +
            "ORDER BY FUNCTION('DATE_FORMAT', t.createdAt, '%Y') ASC")
    List<Object[]> getRevenueByYear(@Param("status") TransactionStatus status,
                                    @Param("startDate") LocalDateTime startDate,
                                    @Param("endDate") LocalDateTime endDate);

    Optional<Transaction> findTopByRelatedAddendum_IdOrderByCreatedAtDesc(Long addendumId);
}

