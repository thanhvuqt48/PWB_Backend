package com.fpt.producerworkbench.repository;

import com.fpt.producerworkbench.common.InspirationType;
import com.fpt.producerworkbench.entity.InspirationItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InspirationItemRepository extends JpaRepository<InspirationItem, Long> {
    Page<InspirationItem> findByProject_IdOrderByCreatedAtDesc(Long projectId, Pageable pageable);
    Page<InspirationItem> findByProject_IdAndTypeOrderByCreatedAtDesc(Long projectId, InspirationType type, Pageable pageable);
}