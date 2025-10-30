package com.fpt.producerworkbench.entity;

import com.fpt.producerworkbench.common.SubscriptionOrderType;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "subscription_orders")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionOrder extends AbstractEntity<Long> {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pro_package_id", nullable = false)
    private ProPackage proPackage;

    @Enumerated(EnumType.STRING)
    @Column(name = "order_type", nullable = false)
    private SubscriptionOrderType orderType;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id", unique = true)
    private Transaction transaction;
}


