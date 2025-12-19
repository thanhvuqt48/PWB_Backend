package com.fpt.producerworkbench.repository;

import com.fpt.producerworkbench.common.TaxDeclarationStatus;
import com.fpt.producerworkbench.common.TaxPeriodType;
import com.fpt.producerworkbench.entity.TaxDeclaration;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TaxDeclarationRepository extends JpaRepository<TaxDeclaration, Long> {
    
    @Query("SELECT td FROM TaxDeclaration td WHERE " +
           "td.taxPeriodType = :periodType " +
           "AND td.taxPeriodYear = :year " +
           "AND td.taxPeriodMonth = :month")
    Optional<TaxDeclaration> findByPeriodTypeAndYearAndMonth(
        @Param("periodType") TaxPeriodType periodType,
        @Param("year") Integer year,
        @Param("month") Integer month
    );
    
    @Query("SELECT td FROM TaxDeclaration td WHERE " +
           "(:year IS NULL OR td.taxPeriodYear = :year) " +
           "AND (:status IS NULL OR td.status = :status)")
    Page<TaxDeclaration> findByFilters(
        @Param("year") Integer year,
        @Param("status") TaxDeclarationStatus status,
        Pageable pageable
    );
}


