package com.fpt.producerworkbench.entity;

import com.fpt.producerworkbench.common.ContractStatus;
import com.fpt.producerworkbench.common.SigningMode;
import com.fpt.producerworkbench.common.SigningOrderType;
import jakarta.persistence.*;
import lombok.*;

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

    @Column(columnDefinition = "TEXT")
    private String content;

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

    @PrePersist
    void prePersist() {
        if (signingMode == null) signingMode = SigningMode.EMAIL;
        if (signingOrderType == null) signingOrderType = SigningOrderType.SEQUENTIAL;
        if (signnowStatus == null) signnowStatus = ContractStatus.DRAFT;
    }

}