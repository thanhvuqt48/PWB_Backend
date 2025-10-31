package com.fpt.producerworkbench.repository;

import com.fpt.producerworkbench.entity.PersonalProject;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PersonalProjectRepository extends JpaRepository<PersonalProject, Long> {
    List<PersonalProject> findByPortfolioId(Long portfolioId);
}

