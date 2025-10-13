package com.fpt.producerworkbench.repository;

import com.fpt.producerworkbench.entity.Contract;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContractRepository extends JpaRepository<Contract, Long> {

}
