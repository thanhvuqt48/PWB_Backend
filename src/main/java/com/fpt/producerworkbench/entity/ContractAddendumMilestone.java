package com.fpt.producerworkbench.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "contract_addendum_milestones",
        indexes = {
                @Index(name = "idx_addendum_milestone_addendum", columnList = "addendum_id")
        })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContractAddendumMilestone extends AbstractEntity<Long> {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "addendum_id", nullable = false)
    private ContractAddendum addendum;

    /** Thứ tự hiển thị của cột mốc trong phụ lục */
    @Column(name = "item_index")
    private Integer itemIndex;

    /** Nội dung chính của cột mốc */
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "num_of_money", precision = 15, scale = 2)
    private BigDecimal numOfMoney;

    @Column(name = "num_of_edit")
    private Integer numOfEdit;

    @Column(name = "num_of_refresh")
    private Integer numOfRefresh;
}
