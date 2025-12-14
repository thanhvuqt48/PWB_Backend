package com.fpt.producerworkbench.entity;

import com.fpt.producerworkbench.common.PaymentType;
import com.fpt.producerworkbench.common.TaxStatus;
import com.fpt.producerworkbench.common.TerminatedBy;
import com.fpt.producerworkbench.common.TerminationType;
import com.fpt.producerworkbench.constant.*;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Ghi nhận thuế khi chấm dứt hợp đồng
 */
@Entity
@Table(name = "tax_records")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaxRecord extends AbstractEntity<Long> {
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contract_id", nullable = false)
    private Contract contract;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "termination_type", nullable = false)
    private TerminationType terminationType; // BEFORE_DAY_20, AFTER_DAY_20
    
    @Enumerated(EnumType.STRING)
    @Column(name = "terminated_by", nullable = false)
    private TerminatedBy terminatedBy; // CLIENT, OWNER
    
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_type", nullable = false)
    private PaymentType paymentType; // FULL, MILESTONE
    
    @Column(name = "termination_date")
    private LocalDate terminationDate;
    
    // === THUẾ GỐC (đã kê khai với chi cục thuế) ===
    @Column(name = "original_tax", precision = 15, scale = 2)
    private BigDecimal originalTax; // Tổng thuế gốc (7%)
    
    // === THUẾ THỰC TẾ (tính trên số tiền thực nhận) ===
    @Column(name = "actual_tax", precision = 15, scale = 2)
    private BigDecimal actualTax; // Thuế thực tế (7%)
    
    // === THUẾ HOÀN LẠI ===
    @Column(name = "refunded_tax", precision = 15, scale = 2)
    private BigDecimal refundedTax; // Thuế được hoàn = Gốc - Thực tế
    
    // === CHI TIẾT ===
    @Column(name = "owner_actual_receive", precision = 15, scale = 2)
    private BigDecimal ownerActualReceive; // Số tiền Owner thực nhận (gross)
    
    @Column(name = "team_compensation", precision = 15, scale = 2)
    private BigDecimal teamCompensation; // Tổng đền bù Team (gross)
    
    @Column(name = "tax_paid_by_owner", precision = 15, scale = 2)
    private BigDecimal taxPaidByOwner; // Thuế Owner đã trả
    
    @Column(name = "tax_paid_by_team", precision = 15, scale = 2)
    private BigDecimal taxPaidByTeam; // Thuế Team đã trả
    
    // Thanh toán 2 lần (nếu sau ngày 20)
    @Column(name = "owner_receive_round_1", precision = 15, scale = 2)
    private BigDecimal ownerReceiveRound1;
    
    @Column(name = "owner_receive_round_2", precision = 15, scale = 2)
    private BigDecimal ownerReceiveRound2;
    
    @Column(name = "refund_scheduled_date")
    private LocalDate refundScheduledDate; // Ngày dự kiến hoàn thuế
    
    @Column(name = "refund_completed_date")
    private LocalDate refundCompletedDate; // Ngày hoàn thuế thực tế
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private TaxStatus status; // COMPLETED, WAITING_REFUND, REFUNDED
    
    @Column(name = "owner_compensation_payment_id")
    private Long ownerCompensationPaymentId; // Link to OwnerCompensationPayment
    
    // Metadata
    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;
}

