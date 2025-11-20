package com.fpt.producerworkbench.repository;

import com.fpt.producerworkbench.entity.SocialLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SocialLinkRepository extends JpaRepository<SocialLink, Long> {
    List<SocialLink> findAllByPortfolioId(Long portfolioId);
}
