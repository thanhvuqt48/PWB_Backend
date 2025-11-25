package com.fpt.producerworkbench.repository;

import com.fpt.producerworkbench.entity.TrackDownloadPermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TrackDownloadPermissionRepository extends JpaRepository<TrackDownloadPermission, Long> {

    /**
     * Tìm quyền download của user cho track cụ thể
     */
    Optional<TrackDownloadPermission> findByTrackIdAndUserId(Long trackId, Long userId);

    /**
     * Kiểm tra user có quyền download track không
     */
    boolean existsByTrackIdAndUserId(Long trackId, Long userId);

    /**
     * Lấy danh sách tất cả users được cấp quyền download cho track
     */
    List<TrackDownloadPermission> findByTrackId(Long trackId);

    /**
     * Xóa quyền download của user cho track
     */
    void deleteByTrackIdAndUserId(Long trackId, Long userId);

    /**
     * Xóa tất cả quyền download của track (khi track bị xóa)
     */
    void deleteByTrackId(Long trackId);
}

