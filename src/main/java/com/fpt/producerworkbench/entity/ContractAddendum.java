package com.fpt.producerworkbench.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "contract_addendums")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContractAddendum extends AbstractEntity<Long> {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contract_id", nullable = false)
    private Contract contract;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    private int version;

    @Column(name = "effective_date")
    private LocalDate effectiveDate;

}