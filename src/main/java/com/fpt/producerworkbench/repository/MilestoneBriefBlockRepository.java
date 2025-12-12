package com.fpt.producerworkbench.repository;

import com.fpt.producerworkbench.entity.MilestoneBriefBlock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MilestoneBriefBlockRepository extends JpaRepository<MilestoneBriefBlock, Long> {
    
    @Modifying
    @Query("DELETE FROM MilestoneBriefBlock b WHERE b.group.id = :groupId")
    void deleteAllByGroupId(@Param("groupId") Long groupId);
}
