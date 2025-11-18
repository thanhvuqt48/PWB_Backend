package com.fpt.producerworkbench.entity;

import com.fpt.producerworkbench.common.CommentStatus;
import jakarta.persistence.*;
import lombok.*;

/**
 * Entity đại diện cho comment trên track nhạc
 * Hỗ trợ comment theo timestamp (giống SoundCloud) và reply dạng cây (parent-child)
 */
@Entity
@Table(name = "track_comments", indexes = {
    @Index(name = "idx_track_id", columnList = "track_id"),
    @Index(name = "idx_user_id", columnList = "user_id"),
    @Index(name = "idx_parent_comment_id", columnList = "parent_comment_id"),
    @Index(name = "idx_status", columnList = "status"),
    @Index(name = "idx_client_delivery_id", columnList = "client_delivery_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrackComment extends AbstractEntity<Long> {

    /**
     * Track mà comment này thuộc về
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "track_id", nullable = false)
    private Track track;

    /**
     * User tạo comment này
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * Nội dung comment
     */
    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    /**
     * Timestamp trong track (giây) - vị trí thời gian mà comment được đặt
     * Giống như SoundCloud, comment sẽ hiển thị tại thời điểm cụ thể trong bài nhạc
     * Null nếu đây là comment chung không gắn với timestamp cụ thể
     */
    @Column(name = "timestamp")
    private Integer timestamp;

    /**
     * Trạng thái xử lý của comment
     * Mặc định là PENDING khi comment được tạo
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private CommentStatus status = CommentStatus.PENDING;

    /**
     * Comment cha (để tạo cấu trúc reply dạng cây)
     * Null nếu đây là comment gốc
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_comment_id")
    private TrackComment parentComment;

    /**
     * Cờ đánh dấu comment đã bị xóa (soft delete)
     */
    @Column(name = "is_deleted", nullable = false)
    @Builder.Default
    private Boolean isDeleted = false;

    /**
     * ClientDelivery mà comment này thuộc về (nếu comment trong Client Room)
     * Null nếu comment trong Internal Room
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_delivery_id")
    private ClientDelivery clientDelivery;
}



