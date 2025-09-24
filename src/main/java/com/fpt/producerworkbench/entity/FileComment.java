package com.fpt.producerworkbench.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "file_comments")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileComment  extends AbstractEntity<Long>{

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "file_id", nullable = false)
    private ProjectFile file;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "comment_text", columnDefinition = "TEXT", nullable = false)
    private String commentText;

    @Column(name = "timestamp_seconds")
    private Float timestampSeconds;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_comment_id")
    private FileComment parentComment;

}