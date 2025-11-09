package com.fpt.producerworkbench.repository;

import com.fpt.producerworkbench.entity.MilestoneMoneySplit;
import com.fpt.producerworkbench.common.MoneySplitStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MilestoneMoneySplitRepository extends JpaRepository<MilestoneMoneySplit, Long> {

    List<MilestoneMoneySplit> findByMilestoneId(Long milestoneId);

    List<MilestoneMoneySplit> findByMilestoneIdAndUserId(Long milestoneId, Long userId);

    Optional<MilestoneMoneySplit> findByMilestoneIdAndUserIdAndStatus(Long milestoneId, Long userId, MoneySplitStatus status);

    boolean existsByMilestoneIdAndUserIdAndStatus(Long milestoneId, Long userId, MoneySplitStatus status);
}


