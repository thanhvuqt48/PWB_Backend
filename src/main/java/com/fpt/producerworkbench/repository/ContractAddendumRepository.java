package com.fpt.producerworkbench.repository;

import com.fpt.producerworkbench.entity.ContractAddendum;
import org.springframework.data.jpa.repository.*;
import org.springframework.stereotype.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

@Repository
public interface ContractAddendumRepository extends JpaRepository<ContractAddendum, Long> {

    @Query("select coalesce(max(a.version), 0) from ContractAddendum a where a.contract.id = :contractId")
    int findMaxVersion(@Param("contractId") Long contractId);

    Optional<ContractAddendum> findFirstByContractIdOrderByVersionDesc(Long contractId);
    Optional<ContractAddendum> findBySignnowDocumentId(String docId);

    // Tìm max addendumNumber của một contract
    @Query("select coalesce(max(a.addendumNumber), 0) from ContractAddendum a where a.contract.id = :contractId")
    int findMaxAddendumNumber(@Param("contractId") Long contractId);

    // Tìm phụ lục mới nhất (theo addendumNumber và version)
    // Sử dụng method naming của Spring Data JPA để tự động giới hạn 1 kết quả
    Optional<ContractAddendum> findFirstByContractIdOrderByAddendumNumberDescVersionDesc(Long contractId);

    // Tìm version mới nhất của một addendumNumber cụ thể
    // Sử dụng method naming của Spring Data JPA để tự động giới hạn 1 kết quả
    Optional<ContractAddendum> findFirstByContractIdAndAddendumNumberOrderByVersionDesc(Long contractId, int addendumNumber);

    // Tìm tất cả phụ lục của một contract, sắp xếp theo addendumNumber và version
    @Query("select a from ContractAddendum a where a.contract.id = :contractId " +
           "order by a.addendumNumber asc, a.version asc")
    List<ContractAddendum> findAllByContractIdOrderByAddendumNumberAndVersion(@Param("contractId") Long contractId);

    // Tìm tất cả version của một addendumNumber
    @Query("select a from ContractAddendum a where a.contract.id = :contractId and a.addendumNumber = :addendumNumber " +
           "order by a.version asc")
    List<ContractAddendum> findAllVersionsByAddendumNumber(@Param("contractId") Long contractId, 
                                                           @Param("addendumNumber") int addendumNumber);

}