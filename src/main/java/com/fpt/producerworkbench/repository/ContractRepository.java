package com.fpt.producerworkbench.repository;

import com.fpt.producerworkbench.entity.Contract;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ContractRepository extends JpaRepository<Contract, Long> {
    Optional<Contract> findByProjectId(Long projectId);
    Optional<Contract> findByReviewToken(String reviewToken);
    @Query("SELECT c FROM Contract c " +
            "JOIN FETCH c.project p " +
            "LEFT JOIN FETCH p.client " +
            "LEFT JOIN FETCH p.creator " +
            "WHERE c.id = :id")
    Optional<Contract> findByIdWithDetails(@Param("id") Long id);

    Optional<Contract> findBySignnowDocumentId(String signnowDocumentId);

}
