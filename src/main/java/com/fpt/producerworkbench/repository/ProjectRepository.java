package com.fpt.producerworkbench.repository;

import com.fpt.producerworkbench.common.ProjectStatus;
import com.fpt.producerworkbench.dto.response.ProjectSummaryResponse;
import com.fpt.producerworkbench.entity.Project;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    boolean existsByTitle(String title);

    @Query("SELECT new com.fpt.producerworkbench.dto.response.ProjectSummaryResponse(" +
            "p.id, p.title, p.description, p.status, p.type, pm.projectRole, " +
            "CONCAT(p.creator.firstName, ' ', p.creator.lastName), " +
            "p.createdAt) " + 
            "FROM Project p JOIN ProjectMember pm ON p.id = pm.project.id " +
            "WHERE pm.user.id = :userId " +
            "AND (:search IS NULL OR LOWER(p.title) LIKE LOWER(CONCAT('%', :search, '%'))) " +
            "AND (:status IS NULL OR p.status = :status)")
    Page<ProjectSummaryResponse> findProjectSummariesByMemberId(
            @Param("userId") Long userId,
            @Param("search") String search,
            @Param("status") ProjectStatus status,
            Pageable pageable);
    @Query("SELECT new com.fpt.producerworkbench.dto.response.ProjectSummaryResponse(" +
            "p.id, p.title, p.description, p.status, p.type, " +
            "com.fpt.producerworkbench.common.ProjectRole.OWNER, " +
            "CONCAT(p.creator.firstName, ' ', p.creator.lastName), " +
            "p.createdAt) " + 
            "FROM Project p " +
            "WHERE p.creator.id = :userId " +
            "AND (:search IS NULL OR LOWER(p.title) LIKE LOWER(CONCAT('%', :search, '%'))) " +
            "AND (:status IS NULL OR p.status = :status)")
    Page<ProjectSummaryResponse> findProjectSummariesByOwnerId(
            @Param("userId") Long userId,
            @Param("search") String search,
            @Param("status") ProjectStatus status,
            Pageable pageable);
}
