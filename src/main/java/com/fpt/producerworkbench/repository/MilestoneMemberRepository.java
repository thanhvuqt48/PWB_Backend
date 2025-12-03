package com.fpt.producerworkbench.repository;

import com.fpt.producerworkbench.entity.MilestoneMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MilestoneMemberRepository extends JpaRepository<MilestoneMember, Long> {

    List<MilestoneMember> findByMilestoneId(Long milestoneId);

    boolean existsByMilestoneIdAndUserId(Long milestoneId, Long userId);

    List<MilestoneMember> findByMilestoneIdInAndUserId(List<Long> milestoneIds, Long userId);

    Optional<MilestoneMember> findByMilestoneIdAndUserId(Long milestoneId, Long userId);

}


