package com.fpt.producerworkbench.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
        name = "contract_fields",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_field_contract_key", columnNames = {"contract_id", "field_key"})
        },
        indexes = { @Index(name = "idx_field_contract", columnList = "contract_id") }
)
public class ContractField extends AbstractEntity<Long> {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contract_id", nullable = false)
    private Contract contract;

    @Column(name = "field_key", length = 255, nullable = false)
    private String fieldKey;

    @Lob
    @Column(name = "field_value")
    private String fieldValue;
}
