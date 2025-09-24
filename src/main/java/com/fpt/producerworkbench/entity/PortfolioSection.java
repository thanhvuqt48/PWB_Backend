package com.fpt.producerworkbench.entity;

import com.fpt.producerworkbench.common.PortfolioSectionType;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "portfolio_sections")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PortfolioSection  extends AbstractEntity<Long>{

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "portfolio_id", nullable = false)
    private Portfolio portfolio;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "section_type")
    private PortfolioSectionType sectionType;

    @Column(name = "display_order")
    private int displayOrder;
}