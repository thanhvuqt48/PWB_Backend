package com.fpt.producerworkbench.repository;

import com.fpt.producerworkbench.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    Optional<Transaction> findByTransactionCode(String transactionCode);

    Optional<Transaction> findTopByRelatedContract_IdOrderByCreatedAtDesc(Long contractId);
}
