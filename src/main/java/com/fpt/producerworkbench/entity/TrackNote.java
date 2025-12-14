package com.fpt.producerworkbench.entity;

import com.fpt.producerworkbench.common.RoomType;
import jakarta.persistence.*;
import lombok.*;

/**
 * Entity đại diện cho ghi chú của một track
 */
@Entity
@Table(name = "track_notes", indexes = {
    @Index(name = "idx_track_notes_track_room", columnList = "track_id, room_type")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrackNote extends AbstractEntity<Long> {

    /**
     * Track mà ghi chú này thuộc về
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "track_id", nullable = false)
    private Track track;

    /**
     * User tạo ghi chú
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * Nội dung ghi chú
     */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    /**
     * Loại phòng (INTERNAL hoặc CLIENT)
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "room_type", nullable = false, length = 20)
    private RoomType roomType;

    /**
     * Thời điểm trong bài hát (giây)
     * Null nếu note không gắn với thời điểm cụ thể
     */
    @Column(name = "timestamp")
    private Double timestamp;
}
