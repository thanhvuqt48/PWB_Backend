package com.fpt.producerworkbench.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "subscription_plans")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionPlan extends AbstractEntity<Long>{

    @Column(nullable = false)
    private String name;

    @Column(precision = 10, scale = 2, nullable = false)
    private BigDecimal price;

    @Column(length = 3, nullable = false)
    private String currency;

    @Column(name = "duration_days", nullable = false)
    private int durationDays;

    @Column(columnDefinition = "TEXT")
    private String description;
}