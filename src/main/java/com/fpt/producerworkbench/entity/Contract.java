package com.fpt.producerworkbench.entity;

import com.fpt.producerworkbench.common.ContractStatus;
import com.fpt.producerworkbench.common.PaymentType;
import com.fpt.producerworkbench.common.SigningMode;
import com.fpt.producerworkbench.common.SigningOrderType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Entity
@Table(
        name = "contracts",
        indexes = {
                @Index(name = "idx_contract_project", columnList = "project_id"),
                @Index(name = "idx_contract_signnow_doc", columnList = "signnow_document_id", unique = true)
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Contract extends AbstractEntity<Long> {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", unique = true, nullable = false)
    private Project project;

    @Column(name = "contract_details", columnDefinition = "TEXT")
    private String contractDetails;

    @Column(name = "total_amount", precision = 15, scale = 2, nullable = false)
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_type", nullable = false)
    private PaymentType paymentType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ContractStatus status;

    @Column(name = "fp_edit_amount")
    private Integer fpEditAmount;

    @Column(name = "signnow_template_id", length = 128)
    private String signnowTemplateId;

    @Column(name = "signnow_document_id", length = 128, unique = true)
    private String signnowDocumentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "signnow_status", length = 32, nullable = false)
    private ContractStatus signnowStatus = ContractStatus.DRAFT;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "signing_mode", length = 16, nullable = false)
    private SigningMode signingMode = SigningMode.EMAIL;

    @Builder.Default // Thêm annotation này
    @Enumerated(EnumType.STRING)
    @Column(name = "signing_order_type", length = 16, nullable = false)
    private SigningOrderType signingOrderType = SigningOrderType.SEQUENTIAL;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "expires_at")
    private Date expiresAt;

    @Lob
    @Column(name = "last_error")
    private String lastError;

    @Builder.Default
    @OneToMany(mappedBy = "contract", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("version ASC")
    private List<ContractDocument> documents = new ArrayList<>();

    @Column(name = "review_token", length = 64, unique = true)
    private String reviewToken;

    @Column(name = "review_token_expires_at")
    private LocalDateTime reviewTokenExpiresAt;

}