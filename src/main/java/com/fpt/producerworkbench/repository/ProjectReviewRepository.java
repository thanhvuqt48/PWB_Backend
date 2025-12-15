package com.fpt.producerworkbench.repository;

import com.fpt.producerworkbench.entity.ProjectReview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProjectReviewRepository extends JpaRepository<ProjectReview, Long> {

    boolean existsByProjectId(Long projectId);

    Optional<ProjectReview> findByProjectId(Long projectId);

    @Query("SELECT r FROM ProjectReview r WHERE r.targetUser.id = :producerId AND r.allowPublicPortfolio = true ORDER BY r.createdAt DESC")
    List<ProjectReview> findPublicPortfolioByProducerId(@Param("producerId") Long producerId);
}