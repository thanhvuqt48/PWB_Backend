package com.fpt.producerworkbench.entity;

import com.fpt.producerworkbench.common.ClientDeliveryStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Entity đại diện cho việc gửi track từ phòng nội bộ cho khách hàng
 * Mỗi track chỉ được gửi 1 lần cho 1 milestone
 */
@Entity
@Table(name = "client_deliveries", 
       uniqueConstraints = @UniqueConstraint(name = "uk_track_milestone", columnNames = {"track_id", "milestone_id"}),
       indexes = {
           @Index(name = "idx_milestone_status", columnList = "milestone_id, status"),
           @Index(name = "idx_track", columnList = "track_id"),
           @Index(name = "idx_sent_by", columnList = "sent_by"),
           @Index(name = "idx_sent_at", columnList = "sent_at")
       })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClientDelivery extends AbstractEntity<Long> {

    /**
     * Track được gửi cho client
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "track_id", nullable = false)
    private Track track;

    /**
     * Milestone mà track được gửi trong đó
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "milestone_id", nullable = false)
    private Milestone milestone;

    /**
     * User đã gửi track (Owner)
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sent_by", nullable = false)
    private User sentBy;

    /**
     * Trạng thái delivery từ phía client
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ClientDeliveryStatus status = ClientDeliveryStatus.DELIVERED;

    /**
     * Thời điểm gửi track
     */
    @Column(name = "sent_at", nullable = false)
    private LocalDateTime sentAt;

    /**
     * Ghi chú khi gửi hoặc khi client phản hồi
     */
    @Column(columnDefinition = "TEXT")
    private String note;

    /**
     * Relationship với MilestoneDelivery (để tracking product count)
     */
    @OneToOne(mappedBy = "clientDelivery", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private MilestoneDelivery milestoneDelivery;
}

