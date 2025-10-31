package com.fpt.producerworkbench.repository;

import com.fpt.producerworkbench.entity.SubscriptionOrder;
import com.fpt.producerworkbench.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SubscriptionOrderRepository extends JpaRepository<SubscriptionOrder, Long> {
    Optional<SubscriptionOrder> findByTransaction(Transaction transaction);
}


