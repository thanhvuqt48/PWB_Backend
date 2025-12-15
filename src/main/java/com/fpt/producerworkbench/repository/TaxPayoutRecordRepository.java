package com.fpt.producerworkbench.repository;

import com.fpt.producerworkbench.common.PayoutSource;
import com.fpt.producerworkbench.common.PayoutStatus;
import com.fpt.producerworkbench.entity.TaxPayoutRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface TaxPayoutRecordRepository extends JpaRepository<TaxPayoutRecord, Long> {
    
    List<TaxPayoutRecord> findByUserId(Long userId);
    
    Page<TaxPayoutRecord> findByUserId(Long userId, Pageable pageable);
    
    List<TaxPayoutRecord> findByUserIdAndPayoutDateBetween(
        Long userId, 
        LocalDate startDate, 
        LocalDate endDate
    );
    
    @Query("SELECT tpr FROM TaxPayoutRecord tpr WHERE tpr.user.id = :userId " +
           "AND tpr.taxPeriodYear = :year " +
           "AND (:source IS NULL OR tpr.payoutSource = :source)")
    Page<TaxPayoutRecord> findUserHistory(
        @Param("userId") Long userId,
        @Param("year") Integer year,
        @Param("source") PayoutSource source,
        Pageable pageable
    );
    
    @Query("SELECT tpr FROM TaxPayoutRecord tpr WHERE " +
           "(:userId IS NULL OR tpr.user.id = :userId) " +
           "AND (:fromDate IS NULL OR tpr.payoutDate >= :fromDate) " +
           "AND (:toDate IS NULL OR tpr.payoutDate <= :toDate) " +
           "AND (:isDeclared IS NULL OR tpr.isTaxDeclared = :isDeclared)")
    Page<TaxPayoutRecord> findByFilters(
        @Param("userId") Long userId,
        @Param("fromDate") LocalDate fromDate,
        @Param("toDate") LocalDate toDate,
        @Param("isDeclared") Boolean isDeclared,
        Pageable pageable
    );
    
    @Query("SELECT tpr FROM TaxPayoutRecord tpr WHERE " +
           "tpr.user.id = :userId " +
           "AND (:fromDate IS NULL OR tpr.payoutDate >= :fromDate) " +
           "AND (:toDate IS NULL OR tpr.payoutDate <= :toDate) " +
           "AND (:source IS NULL OR tpr.payoutSource = :source) " +
           "AND (:status IS NULL OR tpr.status = :status)")
    Page<TaxPayoutRecord> findByUserAndDateRange(
        @Param("userId") Long userId,
        @Param("fromDate") LocalDate fromDate,
        @Param("toDate") LocalDate toDate,
        @Param("source") PayoutSource source,
        @Param("status") PayoutStatus status,
        Pageable pageable
    );
}


