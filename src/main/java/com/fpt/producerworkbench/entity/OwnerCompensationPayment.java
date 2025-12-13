package com.fpt.producerworkbench.entity;

import com.fpt.producerworkbench.common.PaymentStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Thanh toán đền bù của Owner cho Team (khi Owner chấm dứt hợp đồng)
 * Owner phải chuyển tiền TỪ TÚI qua PayOS
 */
@Entity
@Table(name = "owner_compensation_payments")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OwnerCompensationPayment extends AbstractEntity<Long> {
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contract_id", nullable = false)
    private Contract contract;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;
    
    // Số tiền Owner phải trả (gross - bao gồm cả thuế)
    @Column(name = "total_amount", precision = 15, scale = 2, nullable = false)
    private BigDecimal totalAmount;
    
    // Thông tin PayOS
    @Column(name = "payment_order_id")
    private String paymentOrderId; // ID từ PayOS
    
    @Column(name = "payment_order_code")
    private String paymentOrderCode; // Order code từ PayOS
    
    @Column(name = "payment_url", columnDefinition = "TEXT")
    private String paymentUrl; // Link thanh toán cho Owner
    
    // Trạng thái
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PaymentStatus status;
    // PENDING    - Đang chờ Owner chuyển tiền
    // PROCESSING - Đang xử lý
    // COMPLETED  - Owner đã chuyển tiền, Team đã nhận
    // FAILED     - Thất bại
    // EXPIRED    - Hết hạn (Owner không chuyển tiền)
    
    @Column(name = "completed_at")
    private LocalDateTime completedAt;
    
    @Column(name = "expired_at")
    private LocalDateTime expiredAt; // Hết hạn sau 24h
    
    // Ghi chú
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;
    
    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;
    
    /**
     * Lý do chấm dứt hợp đồng từ request ban đầu của Owner
     * Lưu lại để dùng khi hoàn tất chấm dứt hợp đồng sau khi thanh toán
     */
    @Column(name = "termination_reason", columnDefinition = "TEXT")
    private String terminationReason;
}


