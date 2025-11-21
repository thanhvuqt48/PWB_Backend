package com.fpt.producerworkbench.repository;

import com.fpt.producerworkbench.entity.InspirationTrack;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TrackRepository extends JpaRepository<InspirationTrack, Long> {
    List<InspirationTrack> findByProject_IdAndUploader_IdOrderByCreatedAtDesc(Long projectId, Long uploaderId);
}
