package com.fpt.producerworkbench.repository;

import com.fpt.producerworkbench.entity.Milestone;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface MilestoneRepository extends JpaRepository<Milestone, Long> {
    
    @Query("SELECT m FROM Milestone m WHERE m.contract.id = :contractId ORDER BY m.sequence ASC LIMIT 1")
    Optional<Milestone> findFirstByContractIdOrderBySequenceAsc(@Param("contractId") Long contractId);
}
