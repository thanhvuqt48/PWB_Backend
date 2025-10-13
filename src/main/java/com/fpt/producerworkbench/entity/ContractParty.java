package com.fpt.producerworkbench.entity;

import com.fpt.producerworkbench.common.PartyRole;
import com.fpt.producerworkbench.common.PartyStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Date;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
        name = "contract_parties",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_party_contract_role_email", columnNames = {"contract_id", "role", "email"})
        },
        indexes = {
                @Index(name = "idx_party_contract", columnList = "contract_id"),
                @Index(name = "idx_party_email", columnList = "email")
        }
)
public class ContractParty extends AbstractEntity<Long> {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contract_id", nullable = false)
    private Contract contract;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", length = 16, nullable = false)
    private PartyRole role;

    @Column(name = "full_name", length = 255, nullable = false)
    private String fullName;

    @Column(name = "email", length = 255, nullable = false)
    private String email;

    @Column(name = "phone", length = 32)
    private String phone;

    @Column(name = "sign_order")
    private Integer signOrder;

    @Column(name = "signnow_role_id", length = 128)
    private String signnowRoleId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 16, nullable = false)
    private PartyStatus status = PartyStatus.PENDING;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "signed_at")
    private Date signedAt;
}
