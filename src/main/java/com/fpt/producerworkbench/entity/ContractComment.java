package com.fpt.producerworkbench.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "contract_comments")
@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
public class ContractComment extends AbstractEntity<Long> {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contract_id", nullable = false)
    private Contract contract;

    @Lob
    @Column(name = "comment", columnDefinition = "TEXT", nullable = false)
    private String comment;

}