package com.fpt.producerworkbench.entity;

import com.fpt.producerworkbench.common.ContractDocumentType;
import jakarta.persistence.*;
import lombok.*;

@Builder
@Data
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@Entity
@Table(
        name = "contract_documents",
        indexes = {
                @Index(name = "idx_doc_contract", columnList = "contract_id"),
                @Index(name = "idx_doc_signnow_docid", columnList = "signnow_document_id", unique = true)
        }
)
public class ContractDocument extends AbstractEntity<Long> {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contract_id", nullable = false)
    private Contract contract;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", length = 32, nullable = false)
    private ContractDocumentType type; // ORIGINAL, FILLED, SIGNNOW_UPLOADED, SIGNED_FINAL, AUDIT_TRAIL

    @Lob
    @Column(name = "storage_url")
    private String storageUrl; // link S3

    @Column(name = "signnow_document_id", length = 128)
    private String signnowDocumentId;

    @Column(name = "version")
    private Integer version;
}
