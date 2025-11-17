package com.fpt.producerworkbench.entity;

import com.fpt.producerworkbench.common.ProcessingStatus;
import com.fpt.producerworkbench.common.TrackStatus;
import jakarta.persistence.*;
import lombok.*;

/**
 * Entity đại diện cho một sản phẩm nhạc (Track) trong milestone
 * Dùng cho phòng nội bộ giữa Owner và COLLABORATOR
 */
@Entity
@Table(name = "tracks")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Track extends AbstractEntity<Long> {

    /**
     * Tên bài nhạc
     */
    @Column(nullable = false)
    private String name;

    /**
     * Mô tả về bài nhạc
     */
    @Column(columnDefinition = "TEXT")
    private String description;

    /**
     * Version của bài nhạc (v1, v2, final, ...)
     */
    @Column(nullable = false)
    private String version;

    /**
     * Milestone chứa track này
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "milestone_id", nullable = false)
    private Milestone milestone;

    /**
     * User đã tạo track (Owner hoặc COLLABORATOR)
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * Key của file master (bản gốc chất lượng cao) trên S3
     * Ví dụ: audio/original/{trackId}/master.wav
     */
    @Column(name = "s3_original_key")
    private String s3OriginalKey;

    /**
     * Prefix thư mục HLS trên S3 (chứa index.m3u8 và các segments)
     * Ví dụ: audio/hls/{trackId}/
     */
    @Column(name = "hls_prefix")
    private String hlsPrefix;

    /**
     * Key của file voice tag audio trên S3
     * Ví dụ: audio/voice-tag/{trackId}/tag.mp3
     */
    @Column(name = "voice_tag_audio_key")
    private String voiceTagAudioKey;

    /**
     * Cờ bật/tắt voice tag cho track này
     */
    @Column(name = "voice_tag_enabled", nullable = false)
    @Builder.Default
    private Boolean voiceTagEnabled = false;

    /**
     * Nội dung text voice tag do Owner/COLLABORATOR nhập
     * Ví dụ: "Demo thuộc về Producer X, chỉ dùng để nghe trước."
     */
    @Column(name = "voice_tag_text", columnDefinition = "TEXT")
    private String voiceTagText;

    /**
     * Trạng thái nghiệp vụ nội bộ
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private TrackStatus status = TrackStatus.INTERNAL_DRAFT;

    /**
     * Trạng thái xử lý file kỹ thuật
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "processing_status", nullable = false)
    @Builder.Default
    private ProcessingStatus processingStatus = ProcessingStatus.UPLOADING;

    /**
     * Thông báo lỗi nếu processing failed
     */
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    /**
     * Content type của file master (audio/wav, audio/mp3, ...)
     */
    @Column(name = "content_type")
    private String contentType;

    /**
     * Kích thước file master (bytes)
     */
    @Column(name = "file_size")
    private Long fileSize;

    /**
     * Thời lượng của track (seconds)
     */
    @Column(name = "duration")
    private Integer duration;

    /**
     * ID của track version đầu tiên (root track)
     * Track version đầu tiên có rootTrackId = null hoặc chính ID của nó
     * Tất cả các version sau sẽ có cùng rootTrackId để FE có thể group và hiển thị dạng cây
     */
    @Column(name = "root_track_id")
    private Long rootTrackId;

    /**
     * ID của track cha trực tiếp (parent track)
     * Track version đầu tiên có parentTrackId = null
     * Các version sau có parentTrackId = id của track mà nó được tạo từ đó
     * Dùng để xây dựng cây phân cấp chi tiết (Track 1 -> Track 2 -> Track 3)
     */
    @Column(name = "parent_track_id")
    private Long parentTrackId;
}

