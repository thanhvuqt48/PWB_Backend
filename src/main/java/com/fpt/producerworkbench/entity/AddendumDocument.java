package com.fpt.producerworkbench.entity;

import com.fpt.producerworkbench.common.AddendumDocumentType;
import jakarta.persistence.*;
import lombok.*;

@Builder
@Data
@EqualsAndHashCode(callSuper = false)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        name = "addendum_documents",
        indexes = {
                @Index(name = "idx_addendum_doc_addendum", columnList = "addendum_id"),
                @Index(name = "idx_addendum_doc_signnow_docid", columnList = "signnow_document_id", unique = true)
        }
)
public class AddendumDocument extends AbstractEntity<Long> {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "addendum_id", nullable = false)
    private ContractAddendum addendum;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", length = 32, nullable = false)
    private AddendumDocumentType type; // FILLED, SIGNED

    @Lob
    @Column(name = "storage_url")
    private String storageUrl; // link S3

    @Column(name = "signnow_document_id", length = 128)
    private String signnowDocumentId;

    @Column(name = "version")
    private Integer version;
}

