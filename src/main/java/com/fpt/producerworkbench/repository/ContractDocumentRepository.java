package com.fpt.producerworkbench.repository;

import com.fpt.producerworkbench.common.ContractDocumentType;
import com.fpt.producerworkbench.entity.ContractDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface ContractDocumentRepository extends JpaRepository<ContractDocument, Long> {

    Optional<ContractDocument>
    findTopByContract_IdAndTypeOrderByVersionDesc(Long contractId, ContractDocumentType type);

    @Query("select coalesce(max(d.version),0) from ContractDocument d " +
            "where d.contract.id = :contractId and d.type = :type")
    int findMaxVersion(Long contractId, ContractDocumentType type);
}
