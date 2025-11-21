package com.fpt.producerworkbench.entity;

import com.fpt.producerworkbench.common.DeliveryStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Entity tracking việc sử dụng product_count và edit_count của milestone
 * - REJECTED: productCountUsed = 1, editCountUsed = 0
 * - REQUEST_EDIT: productCountUsed = 0, editCountUsed = 1
 */
@Entity
@Table(name = "milestone_deliveries",
       indexes = {
           @Index(name = "idx_milestone_status", columnList = "milestone_id, status"),
           @Index(name = "idx_track", columnList = "track_id"),
           @Index(name = "idx_client_delivery", columnList = "client_delivery_id")
       })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MilestoneDelivery extends AbstractEntity<Long> {

    /**
     * Milestone được trừ product_count
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "milestone_id", nullable = false)
    private Milestone milestone;

    /**
     * Track được gửi
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "track_id", nullable = false)
    private Track track;

    /**
     * Client delivery tương ứng
     */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_delivery_id", nullable = false)
    private ClientDelivery clientDelivery;

    /**
     * User đã gửi (Owner)
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "delivered_by", nullable = false)
    private User deliveredBy;

    /**
     * Số lượng product_count được sử dụng (mặc định = 0)
     */
    @Column(name = "product_count_used", nullable = false)
    @Builder.Default
    private Integer productCountUsed = 0;

    /**
     * Số lượng edit_count được sử dụng (mặc định = 0)
     */
    @Column(name = "edit_count_used", nullable = false)
    @Builder.Default
    private Integer editCountUsed = 0;

    /**
     * Trạng thái của delivery (ACTIVE = đang tính vào quota, CANCELLED = không tính)
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private DeliveryStatus status = DeliveryStatus.ACTIVE;

    /**
     * Thời điểm gửi
     */
    @Column(name = "delivered_at", nullable = false)
    private LocalDateTime deliveredAt;
}

