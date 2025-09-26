package com.fpt.producerworkbench.repository;

import com.fpt.producerworkbench.entity.Portfolio;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface PortfolioRepository extends JpaRepository<Portfolio, Long>, JpaSpecificationExecutor<Portfolio>, PortfolioRepositoryCustom {
}
