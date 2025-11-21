package com.fpt.producerworkbench.entity;

import com.fpt.producerworkbench.common.TrackStatus;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
        name = "inspiration_tracks",
        indexes = {
                @Index(
                        name = "idx_inspiration_track_project_created",
                        columnList = "project_id, created_at"
                )
        }
)
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class InspirationTrack extends AbstractEntity<Long> {

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "uploader_id", nullable = false)
    private User uploader;

    @Column(name = "file_name", length = 255) private String fileName;
    @Column(name = "mime_type", length = 128) private String mimeType;
    @Column(name = "size_bytes")               private Long sizeBytes;

    @Column(name = "s3_key", length = 1024, nullable = false)
    private String s3Key;

    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 24)
    private TrackStatus status;

    @Lob @Column(name = "lyrics_text", columnDefinition = "TEXT")
    private String lyricsText;

    @Lob @Column(name = "ai_suggestions", columnDefinition = "TEXT")
    private String aiSuggestions;

    @Column(name = "transcribe_job_name", length = 255)
    private String transcribeJobName;
}