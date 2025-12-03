package com.fpt.producerworkbench.repository;

import com.fpt.producerworkbench.dto.response.PackageSalesStat;
import com.fpt.producerworkbench.entity.SubscriptionOrder;
import com.fpt.producerworkbench.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;
import org.springframework.data.repository.query.Param;

public interface SubscriptionOrderRepository extends JpaRepository<SubscriptionOrder, Long> {
    Optional<SubscriptionOrder> findByTransaction(Transaction transaction);


    @Query("SELECT COALESCE(SUM(t.amount), 0) " +
            "FROM SubscriptionOrder s JOIN s.transaction t " +
            "WHERE t.status = 'SUCCESSFUL'")
    BigDecimal sumSubscriptionRevenue();


    @Query("SELECT new com.fpt.producerworkbench.dto.response.PackageSalesStat(p.name, COUNT(s)) " +
            "FROM SubscriptionOrder s JOIN s.proPackage p " +
            "JOIN s.transaction t " +
            "WHERE t.status = 'SUCCESSFUL' AND s.createdAt BETWEEN :startDate AND :endDate " +
            "GROUP BY p.name " +
            "ORDER BY COUNT(s) DESC")
    List<PackageSalesStat> countPackageSales(@Param("startDate") LocalDateTime startDate,
                                             @Param("endDate") LocalDateTime endDate);
}


