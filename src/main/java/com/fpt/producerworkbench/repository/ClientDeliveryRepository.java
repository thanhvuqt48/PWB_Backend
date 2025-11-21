package com.fpt.producerworkbench.repository;

import com.fpt.producerworkbench.common.ClientDeliveryStatus;
import com.fpt.producerworkbench.entity.ClientDelivery;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository cho ClientDelivery
 * Quản lý việc gửi track từ phòng nội bộ cho khách hàng
 */
@Repository
public interface ClientDeliveryRepository extends JpaRepository<ClientDelivery, Long> {

    /**
     * Tìm delivery của track trong milestone cụ thể
     * @param trackId ID của track
     * @param milestoneId ID của milestone
     * @return ClientDelivery nếu tồn tại
     */
    Optional<ClientDelivery> findByTrackIdAndMilestoneId(Long trackId, Long milestoneId);

    /**
     * Tìm các delivery theo milestone và status
     * @param milestoneId ID của milestone
     * @param status Trạng thái delivery
     * @return Danh sách deliveries
     */
    List<ClientDelivery> findByMilestoneIdAndStatus(Long milestoneId, ClientDeliveryStatus status);

    /**
     * Tìm tất cả deliveries của track, sắp xếp theo thời gian gửi
     * @param trackId ID của track
     * @return Danh sách deliveries
     */
    List<ClientDelivery> findByTrackIdOrderBySentAtDesc(Long trackId);

    /**
     * Kiểm tra track đã được gửi cho milestone chưa
     * @param trackId ID của track
     * @param milestoneId ID của milestone
     * @return true nếu đã gửi
     */
    boolean existsByTrackIdAndMilestoneId(Long trackId, Long milestoneId);

    /**
     * Tìm tất cả deliveries của milestone, sắp xếp theo thời gian gửi
     * @param milestoneId ID của milestone
     * @return Danh sách deliveries
     */
    List<ClientDelivery> findByMilestoneIdOrderBySentAtDesc(Long milestoneId);
}

