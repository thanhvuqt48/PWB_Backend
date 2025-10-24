package com.fpt.producerworkbench.repository;

import com.fpt.producerworkbench.entity.Contract;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ContractRepository extends JpaRepository<Contract, Long> {
    Optional<Contract> findByProjectId(Long projectId);
    Optional<Contract> findBySignnowDocumentId(String signnowDocumentId);
}
