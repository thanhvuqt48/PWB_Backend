package com.fpt.producerworkbench.repository;

import com.fpt.producerworkbench.common.MilestoneBriefScope;
import com.fpt.producerworkbench.entity.MilestoneBriefGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MilestoneBriefGroupRepository extends JpaRepository<MilestoneBriefGroup, Long> {


    List<MilestoneBriefGroup> findByMilestoneIdAndScopeOrderByPositionAsc(Long milestoneId,
                                                                          MilestoneBriefScope scope);

    void deleteByMilestoneIdAndScope(Long milestoneId, MilestoneBriefScope scope);
}
