package com.fpt.producerworkbench.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.fpt.producerworkbench.common.TerminatedBy;
import com.fpt.producerworkbench.common.TerminationStatus;
import com.fpt.producerworkbench.common.TerminationType;

/**
 * Thông tin chấm dứt hợp đồng
 */
@Entity
@Table(name = "contract_terminations")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContractTermination extends AbstractEntity<Long> {
    
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contract_id", nullable = false, unique = true)
    private Contract contract;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "terminated_by", nullable = false)
    private TerminatedBy terminatedBy;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "termination_type", nullable = false)
    private TerminationType terminationType;
    
    @Column(name = "termination_date", nullable = false)
    private LocalDateTime terminationDate;
    
    // Tổng quan tài chính
    @Column(name = "total_contract_amount", precision = 15, scale = 2)
    private BigDecimal totalContractAmount;
    
    @Column(name = "total_team_compensation", precision = 15, scale = 2)
    private BigDecimal totalTeamCompensation;
    
    @Column(name = "total_owner_compensation", precision = 15, scale = 2)
    private BigDecimal totalOwnerCompensation;
    
    @Column(name = "total_client_refund", precision = 15, scale = 2)
    private BigDecimal totalClientRefund;
    
    @Column(name = "total_tax_deducted", precision = 15, scale = 2)
    private BigDecimal totalTaxDeducted;
    
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tax_record_id")
    private TaxRecord taxRecord;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private TerminationStatus status;
    
    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;
    
    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason; // Lý do chấm dứt
}


