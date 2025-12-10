package com.fpt.producerworkbench.repository;

import com.fpt.producerworkbench.common.WithdrawalStatus;
import com.fpt.producerworkbench.entity.Withdrawal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WithdrawalRepository extends JpaRepository<Withdrawal, Long>, JpaSpecificationExecutor<Withdrawal> {
    
    Page<Withdrawal> findByUserId(Long userId, Pageable pageable);
    
    Page<Withdrawal> findByStatus(WithdrawalStatus status, Pageable pageable);
    
    Optional<Withdrawal> findByWithdrawalCode(String withdrawalCode);
    
    List<Withdrawal> findByUserIdAndStatus(Long userId, WithdrawalStatus status);
    
    default Page<Withdrawal> search(Specification<Withdrawal> spec, Pageable pageable) {
        return findAll(spec, pageable);
    }
}

