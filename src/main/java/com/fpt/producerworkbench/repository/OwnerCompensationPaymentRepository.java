package com.fpt.producerworkbench.repository;

import com.fpt.producerworkbench.common.PaymentStatus;
import com.fpt.producerworkbench.entity.OwnerCompensationPayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OwnerCompensationPaymentRepository extends JpaRepository<OwnerCompensationPayment, Long> {
    
    Optional<OwnerCompensationPayment> findByContractId(Long contractId);
    
    Optional<OwnerCompensationPayment> findByPaymentOrderCode(String orderCode);
    
    List<OwnerCompensationPayment> findByOwnerId(Long ownerId);
    
    List<OwnerCompensationPayment> findByStatus(PaymentStatus status);
}


