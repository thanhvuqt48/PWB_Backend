package com.fpt.producerworkbench.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Map;

/**
 * Entity lưu lại toàn bộ lịch sử chuyển đổi trạng thái của Track
 * Phục vụ audit trail và FSM validation
 */
@Entity
@Table(name = "track_status_transition_logs",
       indexes = {
           @Index(name = "idx_track_created", columnList = "track_id, created_at"),
           @Index(name = "idx_triggered_by", columnList = "triggered_by"),
           @Index(name = "idx_to_status", columnList = "to_status")
       })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrackStatusTransitionLog extends AbstractEntity<Long> {

    /**
     * Track có thay đổi trạng thái
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "track_id", nullable = false)
    private Track track;

    /**
     * Trạng thái cũ (từ)
     */
    @Column(name = "from_status", length = 50, nullable = false)
    private String fromStatus;

    /**
     * Trạng thái mới (đến)
     */
    @Column(name = "to_status", length = 50, nullable = false)
    private String toStatus;

    /**
     * User trigger transition này
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "triggered_by", nullable = false)
    private User triggeredBy;

    /**
     * Lý do chuyển đổi (nếu có)
     */
    @Column(columnDefinition = "TEXT")
    private String reason;

    /**
     * Metadata bổ sung (JSON format)
     * Ví dụ: {"delivery_id": 123, "client_delivery_status": "DELIVERED"}
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "json")
    private Map<String, Object> metadata;
}

