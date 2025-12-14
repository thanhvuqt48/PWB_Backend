package com.fpt.producerworkbench.repository;

import com.fpt.producerworkbench.common.TerminationStatus;
import com.fpt.producerworkbench.entity.ContractTermination;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ContractTerminationRepository extends JpaRepository<ContractTermination, Long> {
    
    Optional<ContractTermination> findByContractId(Long contractId);
    
    List<ContractTermination> findByStatus(TerminationStatus status);
    
    boolean existsByContractId(Long contractId);
}


