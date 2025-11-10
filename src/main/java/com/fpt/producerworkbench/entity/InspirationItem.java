package com.fpt.producerworkbench.entity;

import com.fpt.producerworkbench.common.InspirationType;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Entity
@Table(
        name = "inspiration_items",
        indexes = {
                @Index(name = "idx_insp_project_created", columnList = "project_id, created_at")
        }
)
@Getter @Setter
@Builder @NoArgsConstructor @AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class InspirationItem extends AbstractEntity<Long> {

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "project_id", nullable = false)
    Project project;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "uploader_id", nullable = false)
    User uploader;

    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16)
    InspirationType type;

    @Column(length = 255)
    String title;

    @Column(columnDefinition = "TEXT")
    String noteContent;

    @Column(name = "s3_key", length = 1024)
    String s3Key;

    @Column(name = "mime_type", length = 128)
    String mimeType;

    @Column(name = "size_bytes")
    Long sizeBytes;
}