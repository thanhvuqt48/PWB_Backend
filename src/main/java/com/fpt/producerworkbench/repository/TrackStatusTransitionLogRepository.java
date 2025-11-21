package com.fpt.producerworkbench.repository;

import com.fpt.producerworkbench.entity.TrackStatusTransitionLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository cho TrackStatusTransitionLog
 * Quản lý audit trail của track status transitions
 */
@Repository
public interface TrackStatusTransitionLogRepository extends JpaRepository<TrackStatusTransitionLog, Long> {

    /**
     * Tìm tất cả transition logs của track, sắp xếp theo thời gian tạo
     * @param trackId ID của track
     * @return Danh sách logs
     */
    List<TrackStatusTransitionLog> findByTrackIdOrderByCreatedAtDesc(Long trackId);
}

