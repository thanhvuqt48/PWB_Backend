package com.fpt.producerworkbench.repository;

import com.fpt.producerworkbench.common.DeliveryStatus;
import com.fpt.producerworkbench.entity.MilestoneDelivery;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository cho MilestoneDelivery
 * Quản lý việc tracking product_count và edit_count của milestone
 */
@Repository
public interface MilestoneDeliveryRepository extends JpaRepository<MilestoneDelivery, Long> {

    /**
     * Đếm số lượng delivery theo milestone và status
     * @param milestoneId ID của milestone
     * @param status Trạng thái delivery
     * @return Số lượng deliveries
     */
    long countByMilestoneIdAndStatus(Long milestoneId, DeliveryStatus status);

    /**
     * Tính tổng product_count_used theo milestone và status
     * @param milestoneId ID của milestone
     * @param status Trạng thái delivery
     * @return Tổng product_count_used
     */
    default int sumProductCountUsedByMilestoneIdAndStatus(Long milestoneId, DeliveryStatus status) {
        return findByMilestoneIdAndStatus(milestoneId, status).stream()
                .mapToInt(md -> md.getProductCountUsed() != null ? md.getProductCountUsed() : 0)
                .sum();
    }

    /**
     * Tính tổng edit_count_used theo milestone và status
     * @param milestoneId ID của milestone
     * @param status Trạng thái delivery
     * @return Tổng edit_count_used
     */
    default int sumEditCountUsedByMilestoneIdAndStatus(Long milestoneId, DeliveryStatus status) {
        return findByMilestoneIdAndStatus(milestoneId, status).stream()
                .mapToInt(md -> md.getEditCountUsed() != null ? md.getEditCountUsed() : 0)
                .sum();
    }

    /**
     * Tìm các deliveries theo milestone và status
     * @param milestoneId ID của milestone
     * @param status Trạng thái delivery
     * @return Danh sách deliveries
     */
    List<MilestoneDelivery> findByMilestoneIdAndStatus(Long milestoneId, DeliveryStatus status);

    /**
     * Tìm MilestoneDelivery theo ClientDelivery
     * @param clientDeliveryId ID của client delivery
     * @return MilestoneDelivery nếu tồn tại
     */
    Optional<MilestoneDelivery> findByClientDeliveryId(Long clientDeliveryId);

    /**
     * Tìm các deliveries của track
     * @param trackId ID của track
     * @return Danh sách deliveries
     */
    List<MilestoneDelivery> findByTrackId(Long trackId);
}

