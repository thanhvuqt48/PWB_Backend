package com.fpt.producerworkbench.repository;

import com.fpt.producerworkbench.common.ContractDocumentType;
import com.fpt.producerworkbench.entity.ContractAddendum;
import com.fpt.producerworkbench.entity.ContractDocument;
import org.springframework.data.jpa.repository.*;
import org.springframework.stereotype.Repository;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

@Repository
public interface ContractAddendumRepository extends JpaRepository<ContractAddendum, Long> {

    @Query("select coalesce(max(a.version), 0) from ContractAddendum a where a.contract.id = :contractId")
    int findMaxVersion(@Param("contractId") Long contractId);

    Optional<ContractAddendum> findFirstByContractIdOrderByVersionDesc(Long contractId);
    Optional<ContractAddendum> findBySignnowDocumentId(String docId);

}