package com.fpt.producerworkbench.repository;

import com.fpt.producerworkbench.entity.Contract;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ContractRepository extends JpaRepository<Contract, Long> {
    Optional<Contract> findByProjectId(Long projectId);
    
    @Query("SELECT c FROM Contract c JOIN FETCH c.project WHERE c.project.id = :projectId")
    Optional<Contract> findByProjectIdWithProject(@Param("projectId") Long projectId);
    
    Optional<Contract> findBySignnowDocumentId(String signnowDocumentId);
}
