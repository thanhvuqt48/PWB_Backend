package com.fpt.producerworkbench.repository;

import com.fpt.producerworkbench.entity.Contract;
import com.fpt.producerworkbench.entity.ContractParty;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ContractPartyRepository extends JpaRepository<ContractParty, Long> {

    Optional<ContractParty> findByContract(Contract contract);

    Optional<ContractParty> findByContractId(Long contractId);
}


