package com.fpt.producerworkbench.repository;

import com.fpt.producerworkbench.entity.PortfolioSection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PortfolioSectionRepository extends JpaRepository<PortfolioSection, Long> {
}
