package com.fpt.producerworkbench.repository;

import com.fpt.producerworkbench.common.TaxPeriodType;
import com.fpt.producerworkbench.entity.UserTaxSummary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserTaxSummaryRepository extends JpaRepository<UserTaxSummary, Long> {
    
    @Query("SELECT uts FROM UserTaxSummary uts WHERE uts.user.id = :userId " +
           "AND uts.taxPeriodType = :periodType " +
           "AND uts.taxPeriodYear = :year " +
           "AND (:monthOrQuarter IS NULL OR " +
           "     uts.taxPeriodMonth = :monthOrQuarter OR " +
           "     uts.taxPeriodQuarter = :monthOrQuarter)")
    Optional<UserTaxSummary> findByUserAndPeriod(
        @Param("userId") Long userId,
        @Param("periodType") TaxPeriodType periodType,
        @Param("year") Integer year,
        @Param("monthOrQuarter") Integer monthOrQuarter
    );
    
    @Query("SELECT uts FROM UserTaxSummary uts WHERE " +
           "uts.taxPeriodYear = :year " +
           "AND (:month IS NULL OR uts.taxPeriodMonth = :month)")
    Page<UserTaxSummary> findByYearAndMonth(
        @Param("year") Integer year,
        @Param("month") Integer month,
        Pageable pageable
    );
}


