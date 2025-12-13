package com.fpt.producerworkbench.repository;

import com.fpt.producerworkbench.common.TaxStatus;
import com.fpt.producerworkbench.entity.TaxRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface TaxRecordRepository extends JpaRepository<TaxRecord, Long> {
    
    Optional<TaxRecord> findByContractId(Long contractId);
    
    List<TaxRecord> findByStatusAndRefundScheduledDate(TaxStatus status, LocalDate refundScheduledDate);
    
    @Query("SELECT tr FROM TaxRecord tr WHERE tr.status = :status " +
           "AND tr.refundScheduledDate <= :date")
    List<TaxRecord> findPendingRefunds(@Param("status") TaxStatus status, 
                                       @Param("date") LocalDate date);
}


