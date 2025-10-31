package com.fpt.producerworkbench.repository;

import com.fpt.producerworkbench.entity.Portfolio;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PortfolioRepository extends JpaRepository<Portfolio, Long>, JpaSpecificationExecutor<Portfolio>, PortfolioRepositoryCustom {

    boolean existsByUserId(Long userId);

    Optional<Portfolio> findByUserId(Long userId);

    Optional<Portfolio> findByCustomUrlSlug(String customUrlSlug);
}
