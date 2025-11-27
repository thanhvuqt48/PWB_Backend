package com.fpt.producerworkbench.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Entity lưu quyền download cho từng track cụ thể
 * Chủ dự án có thể cấp quyền download cho các users cụ thể cho từng track
 */
@Entity
@Table(name = "track_download_permissions",
       uniqueConstraints = {
           @UniqueConstraint(name = "uk_track_user", columnNames = {"track_id", "user_id"})
       },
       indexes = {
           @Index(name = "idx_track_download_track", columnList = "track_id"),
           @Index(name = "idx_track_download_user", columnList = "user_id")
       })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrackDownloadPermission extends AbstractEntity<Long> {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "track_id", nullable = false)
    private Track track;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "granted_by", nullable = false)
    private User grantedBy;
}

