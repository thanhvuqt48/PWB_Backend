package com.fpt.producerworkbench.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(
        name = "contract_parties",
        indexes = {
                @Index(name = "idx_contract_party_contract", columnList = "contract_id", unique = true)
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContractParty extends AbstractEntity<Long> {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contract_id", nullable = false, unique = true)
    private Contract contract;

    // BÊN A
    @Column(name = "a_name")
    private String aName;

    /** Số CCCD/CMND/Hộ chiếu của bên A */
    @Column(name = "a_id_number")
    private String aIdNumber;

    @Column(name = "a_id_issue_date")
    private LocalDate aIdIssueDate;

    @Column(name = "a_id_issue_place")
    private String aIdIssuePlace;

    @Column(name = "a_address")
    private String aAddress;

    @Column(name = "a_phone")
    private String aPhone;

    // BÊN B
    @Column(name = "b_name")
    private String bName;

    /** Số CCCD/CMND/Hộ chiếu của bên B */
    @Column(name = "b_id_number")
    private String bIdNumber;

    @Column(name = "b_id_issue_date")
    private LocalDate bIdIssueDate;

    @Column(name = "b_id_issue_place")
    private String bIdIssuePlace;

    @Column(name = "b_address")
    private String bAddress;

    @Column(name = "b_phone")
    private String bPhone;
}


