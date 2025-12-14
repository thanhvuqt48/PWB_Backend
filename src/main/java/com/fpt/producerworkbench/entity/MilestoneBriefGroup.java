package com.fpt.producerworkbench.entity;

import com.fpt.producerworkbench.common.MilestoneBriefScope;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "milestone_brief_groups")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MilestoneBriefGroup extends AbstractEntity<Long> {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "milestone_id", nullable = false)
    private Milestone milestone;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope", nullable = false, length = 32)
    private MilestoneBriefScope scope;

    // Ví dụ: "Intro", "Verse 1", "Chorus"...
    @Column(nullable = false, length = 255)
    private String title;

    // Thứ tự hiển thị của ô to trong bảng
    @Column(name = "position")
    private Integer position;

    @Column(name = "forward_id")
    private Long forwardId;

    /**
     * KHÔNG khởi tạo = new ArrayList<>() tại field level.
     * Để Hibernate quản lý collection, tránh lỗi "Found shared references to a collection".
     */
    @OneToMany(mappedBy = "group", orphanRemoval = true, fetch = FetchType.LAZY)
    private List<MilestoneBriefBlock> blocks;

    public List<MilestoneBriefBlock> getBlocks() {
        if (blocks == null) {
            blocks = new ArrayList<>();
        }
        return blocks;
    }
}
