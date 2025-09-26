package com.fpt.producerworkbench.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "portfolio_tags",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"portfolio_id", "tag_id"})
        })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PortfolioTag extends AbstractEntity<Long> {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "portfolio_id", nullable = false)
    private Portfolio portfolio;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tag_id", nullable = false)
    private Tag tag;
}