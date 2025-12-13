package com.fpt.producerworkbench.repository;

import com.fpt.producerworkbench.entity.MilestoneMoneySplit;
import com.fpt.producerworkbench.common.MoneySplitStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MilestoneMoneySplitRepository extends JpaRepository<MilestoneMoneySplit, Long> {

    List<MilestoneMoneySplit> findByMilestoneId(Long milestoneId);

    List<MilestoneMoneySplit> findByMilestoneIdAndUserId(Long milestoneId, Long userId);

    Optional<MilestoneMoneySplit> findByMilestoneIdAndUserIdAndStatus(Long milestoneId, Long userId, MoneySplitStatus status);

    boolean existsByMilestoneIdAndUserIdAndStatus(Long milestoneId, Long userId, MoneySplitStatus status);

    boolean existsByMilestoneIdAndStatus(Long milestoneId, MoneySplitStatus status);

    List<MilestoneMoneySplit> findByMilestoneIdInAndUserId(List<Long> milestoneIds, Long userId);

    boolean existsByMilestoneIdInAndUserIdAndStatus(List<Long> milestoneIds, Long userId, MoneySplitStatus status);
    
    // New methods for contract termination
    @Query("SELECT mms FROM MilestoneMoneySplit mms WHERE mms.milestone.contract.id = :contractId AND mms.status = 'APPROVED'")
    List<MilestoneMoneySplit> findApprovedByContractId(@Param("contractId") Long contractId);
    
    @Query("SELECT mms FROM MilestoneMoneySplit mms WHERE mms.milestone.id IN :milestoneIds AND mms.status = 'APPROVED'")
    List<MilestoneMoneySplit> findApprovedByMilestoneIds(@Param("milestoneIds") List<Long> milestoneIds);
}


