package com.fpt.producerworkbench.entity;

import com.fpt.producerworkbench.common.FileApprovalStatus;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "project_files")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectFile extends AbstractEntity<Long>{

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "milestone_id")
    private Milestone milestone;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploader_id", nullable = false)
    private User uploader;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "file_path", nullable = false)
    private String filePath;

    @Column(name = "file_type")
    private String fileType;

    private int version;

    @Enumerated(EnumType.STRING)
    @Column(name = "approval_status", nullable = false)
    private FileApprovalStatus approvalStatus;

}