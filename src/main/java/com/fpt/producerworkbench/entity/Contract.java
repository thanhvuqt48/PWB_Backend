package com.fpt.producerworkbench.entity;

import com.fpt.producerworkbench.common.ContractStatus;
import com.fpt.producerworkbench.common.PaymentType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "contracts")
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
    @Column(nullable = false)
    private ContractStatus status;

}