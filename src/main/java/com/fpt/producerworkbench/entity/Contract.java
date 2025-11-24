package com.fpt.producerworkbench.entity;

import com.fpt.producerworkbench.common.ContractStatus;
import com.fpt.producerworkbench.common.PaymentType;
import com.fpt.producerworkbench.common.SigningMode;
import com.fpt.producerworkbench.common.SigningOrderType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
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

    // Gốc của bạn: giữ nguyên
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

    /** Trạng thái nghiệp vụ tổng thể của hợp đồng trong hệ thống của bạn */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ContractStatus status;

    @Column(name = "fp_edit_amount")
    private Integer fpEditAmount;

    @Column(name = "product_count")
    private Integer productCount;

    @Column(name = "compensation_percentage", precision = 5, scale = 2)
    private BigDecimal compensationPercentage;

    // ====== Bổ sung cho SignNow / ký điện tử ======
    @Column(name = "signnow_template_id", length = 128)
    private String signnowTemplateId;

    /** ID document trên SignNow (khi upload file đã fill, hoặc khi tạo từ template) */
    @Column(name = "signnow_document_id", length = 128, unique = true)
    private String signnowDocumentId;

    /** Trạng thái phía SignNow: DRAFT/OUT_FOR_SIGNATURE/COMPLETED/... */
    @Enumerated(EnumType.STRING)
    @Column(name = "signnow_status", length = 32, nullable = false)
    private ContractStatus signnowStatus = ContractStatus.DRAFT;

    /** Hình thức mời ký: EMAIL | EMBEDDED */
    @Enumerated(EnumType.STRING)
    @Column(name = "signing_mode", length = 16, nullable = false)
    private SigningMode signingMode = SigningMode.EMAIL;

    /** Thứ tự ký: SEQUENTIAL | PARALLEL */
    @Enumerated(EnumType.STRING)
    @Column(name = "signing_order_type", length = 16, nullable = false)
    private SigningOrderType signingOrderType = SigningOrderType.SEQUENTIAL;

    /** Thời hạn ký (nếu đặt) */
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "expires_at")
    private Date expiresAt;

    @Column(name = "pit_tax", precision = 15, scale = 2)
    private BigDecimal pitTax;

    @Column(name = "vat_tax", precision = 15, scale = 2)
    private BigDecimal vatTax;

    /** Ghi lỗi cuối cùng khi gọi API ký (nếu có) */
    @Lob
    @Column(name = "last_error")
    private String lastError;

    /** Lý do từ chối hợp đồng */
    @Lob
    @Column(name = "decline_reason")
    private String declineReason;

    @Builder.Default
    @OneToMany(mappedBy = "contract", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("version ASC")
    private List<ContractDocument> documents = new ArrayList<>();

}