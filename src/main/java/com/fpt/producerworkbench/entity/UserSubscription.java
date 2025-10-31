package com.fpt.producerworkbench.entity;

import com.fpt.producerworkbench.common.SubscriptionStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_subscriptions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSubscription extends AbstractEntity<Long>{

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pro_package_id", nullable = false)
    private ProPackage proPackage;

    @Column(name = "start_date", nullable = false)
    private LocalDateTime startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDateTime endDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private SubscriptionStatus status;

    @Column(name = "auto_renew_enabled", nullable = false)
    private boolean autoRenewEnabled;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "grace_until")
    private LocalDateTime graceUntil;

    @Column(name = "is_current", nullable = false)
    private boolean current;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id", unique = true)
    private Transaction transaction;
}