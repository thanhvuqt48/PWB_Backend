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

    /**
     * Thống kê gói Pro theo tháng của một năm cụ thể
     * Trả về dữ liệu theo format: [month, packageName, count]
     */
    @Query("SELECT FUNCTION('DATE_FORMAT', s.createdAt, '%Y-%m') as month, " +
           "p.name as packageName, COUNT(s) as count " +
           "FROM SubscriptionOrder s JOIN s.proPackage p " +
           "JOIN s.transaction t " +
           "WHERE t.status = 'SUCCESSFUL' " +
           "AND FUNCTION('YEAR', s.createdAt) = :year " +
           "GROUP BY FUNCTION('DATE_FORMAT', s.createdAt, '%Y-%m'), p.name " +
           "ORDER BY month ASC")
    List<Object[]> countPackageSalesByMonth(@Param("year") int year);

    /**
     * Thống kê gói Pro theo năm
     * Trả về dữ liệu theo format: [year, packageName, count]
     */
    @Query("SELECT FUNCTION('DATE_FORMAT', s.createdAt, '%Y') as year, " +
           "p.name as packageName, COUNT(s) as count " +
           "FROM SubscriptionOrder s JOIN s.proPackage p " +
           "JOIN s.transaction t " +
           "WHERE t.status = 'SUCCESSFUL' " +
           "AND s.createdAt BETWEEN :startDate AND :endDate " +
           "GROUP BY FUNCTION('DATE_FORMAT', s.createdAt, '%Y'), p.name " +
           "ORDER BY year ASC")
    List<Object[]> countPackageSalesByYear(@Param("startDate") LocalDateTime startDate,
                                           @Param("endDate") LocalDateTime endDate);

    /**
     * Thống kê gói Pro theo tháng của một năm cụ thể (bao gồm doanh thu)
     * Trả về dữ liệu theo format: [month, packageName, count, revenue]
     */
    @Query("SELECT FUNCTION('DATE_FORMAT', s.createdAt, '%Y-%m') as month, " +
           "p.name as packageName, COUNT(s) as count, COALESCE(SUM(t.amount), 0) as revenue " +
           "FROM SubscriptionOrder s JOIN s.proPackage p " +
           "JOIN s.transaction t " +
           "WHERE t.status = 'SUCCESSFUL' " +
           "AND FUNCTION('YEAR', s.createdAt) = :year " +
           "GROUP BY FUNCTION('DATE_FORMAT', s.createdAt, '%Y-%m'), p.name " +
           "ORDER BY month ASC")
    List<Object[]> countPackageSalesByMonthWithRevenue(@Param("year") int year);

    /**
     * Thống kê gói Pro theo năm (bao gồm doanh thu)
     * Trả về dữ liệu theo format: [year, packageName, count, revenue]
     */
    @Query("SELECT FUNCTION('DATE_FORMAT', s.createdAt, '%Y') as year, " +
           "p.name as packageName, COUNT(s) as count, COALESCE(SUM(t.amount), 0) as revenue " +
           "FROM SubscriptionOrder s JOIN s.proPackage p " +
           "JOIN s.transaction t " +
           "WHERE t.status = 'SUCCESSFUL' " +
           "AND s.createdAt BETWEEN :startDate AND :endDate " +
           "GROUP BY FUNCTION('DATE_FORMAT', s.createdAt, '%Y'), p.name " +
           "ORDER BY year ASC")
    List<Object[]> countPackageSalesByYearWithRevenue(@Param("startDate") LocalDateTime startDate,
                                                       @Param("endDate") LocalDateTime endDate);
}


