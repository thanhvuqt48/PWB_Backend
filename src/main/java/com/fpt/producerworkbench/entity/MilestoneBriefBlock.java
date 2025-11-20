package com.fpt.producerworkbench.entity;

import com.fpt.producerworkbench.common.MilestoneBriefBlockType;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "milestone_brief_blocks")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MilestoneBriefBlock extends AbstractEntity<Long> {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    private MilestoneBriefGroup group;

    @Enumerated(EnumType.STRING)
    @Column(name = "block_type", nullable = false, length = 32)
    private MilestoneBriefBlockType type;

    @Column(name = "label", length = 255)
    private String label;

    @Column(name = "content_text", columnDefinition = "TEXT")
    private String contentText;

    @Column(name = "file_key", length = 1024)
    private String fileKey;

    @Column(name = "position")
    private Integer position;
}
