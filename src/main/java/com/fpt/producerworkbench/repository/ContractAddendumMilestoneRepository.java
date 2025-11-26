package com.fpt.producerworkbench.repository;

import com.fpt.producerworkbench.entity.ContractAddendumMilestone;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface ContractAddendumMilestoneRepository
        extends JpaRepository<ContractAddendumMilestone, Long> {

    List<ContractAddendumMilestone> findByAddendumIdOrderByItemIndexAsc(Long addendumId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("delete from ContractAddendumMilestone m where m.addendum.id = :addendumId")
    void deleteByAddendumId(Long addendumId);
}
