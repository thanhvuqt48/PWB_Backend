package com.fpt.producerworkbench.entity;

import com.fpt.producerworkbench.common.ContractStatus;
import com.fpt.producerworkbench.common.SigningMode;
import com.fpt.producerworkbench.common.SigningOrderType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "contract_addendums",
        indexes = {
                @Index(name = "idx_addendum_contract", columnList = "contract_id"),
                @Index(name = "idx_addendum_signnow_doc", columnList = "signnow_document_id", unique = true)
        })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContractAddendum extends AbstractEntity<Long> {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contract_id", nullable = false)
    private Contract contract;

    @Column()
    private String title;

    private int version;

    @Column(name = "effective_date")
    private LocalDate effectiveDate;

    @Column(name = "signnow_document_id", length = 128, unique = true)
    private String signnowDocumentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "signnow_status", length = 32, nullable = false)
    private ContractStatus signnowStatus = ContractStatus.DRAFT;


    @Enumerated(EnumType.STRING)
    @Column(name = "signing_mode", length = 16, nullable = false)
    private SigningMode signingMode = SigningMode.EMAIL;

    @Enumerated(EnumType.STRING)
    @Column(name = "signing_order_type", length = 16, nullable = false)
    private SigningOrderType signingOrderType = SigningOrderType.SEQUENTIAL;

    @Column(name = "num_of_money", precision = 15, scale = 2)
    private BigDecimal numOfMoney;

    @Column(name = "num_of_edit")
    private Integer numOfEdit;

    @Column(name = "num_of_refresh")
    private Integer numOfRefresh;

    @Column(name = "pit_tax", precision = 15, scale = 2)
    private BigDecimal pitTax;

    @Column(name = "vat_tax", precision = 15, scale = 2)
    private BigDecimal vatTax;

    @PrePersist
    void prePersist() {
        if (signingMode == null) signingMode = SigningMode.EMAIL;
        if (signingOrderType == null) signingOrderType = SigningOrderType.SEQUENTIAL;
        if (signnowStatus == null) signnowStatus = ContractStatus.DRAFT;
    }

}