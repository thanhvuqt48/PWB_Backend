package com.fpt.producerworkbench.repository;

import com.fpt.producerworkbench.entity.Contract;
import com.fpt.producerworkbench.entity.Milestone;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MilestoneRepository extends JpaRepository<Milestone, Long> {
    
    @Query("SELECT m FROM Milestone m WHERE m.contract.id = :contractId ORDER BY m.sequence ASC LIMIT 1")
    Optional<Milestone> findFirstByContractIdOrderBySequenceAsc(@Param("contractId") Long contractId);
    
    @Query("SELECT m FROM Milestone m WHERE m.contract.id = :contractId ORDER BY m.sequence ASC")
    List<Milestone> findByContractIdOrderBySequenceAsc(@Param("contractId") Long contractId);
    
    @Query("SELECT m FROM Milestone m WHERE m.contract.project.id = :projectId ORDER BY m.sequence ASC")
    List<Milestone> findByProjectIdOrderBySequenceAsc(@Param("projectId") Long projectId);
    
    @Query("SELECT m FROM Milestone m WHERE m.contract.id = :contractId AND LOWER(TRIM(m.title)) = LOWER(TRIM(:title))")
    Optional<Milestone> findByContractIdAndTitleIgnoreCase(@Param("contractId") Long contractId, @Param("title") String title);

    void deleteByContract(Contract contract);
}
