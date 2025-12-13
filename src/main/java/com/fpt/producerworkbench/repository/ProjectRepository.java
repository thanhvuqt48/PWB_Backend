package com.fpt.producerworkbench.repository;

import com.fpt.producerworkbench.common.ProjectStatus;
import com.fpt.producerworkbench.dto.projection.ProjectBasicInfo;
import com.fpt.producerworkbench.dto.response.ProjectSummaryResponse;
import com.fpt.producerworkbench.entity.Project;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    boolean existsByTitle(String title);

    /**
     * Lấy thông tin cơ bản của Project (không load liveSessions collection).
     * Dùng Projection để tránh lỗi "shared references to a collection".
     */
    @Query("SELECT p.id AS id, p.title AS title, " +
            "p.creator.id AS creatorId, p.creator.email AS creatorEmail, " +
            "CONCAT(p.creator.firstName, ' ', p.creator.lastName) AS creatorFullName, " +
            "p.client.id AS clientId " +
            "FROM Project p WHERE p.id = :projectId")
    Optional<ProjectBasicInfo> findBasicInfoById(@Param("projectId") Long projectId);

    /**
     * Kiểm tra project đã có client chưa.
     */
    @Query("SELECT CASE WHEN p.client IS NOT NULL THEN true ELSE false END FROM Project p WHERE p.id = :projectId")
    boolean hasClient(@Param("projectId") Long projectId);

    /**
     * Update client của project bằng native query để tránh cascade đến liveSessions.
     * Sử dụng method này thay vì projectRepository.save(project) khi chỉ cần update client.
     */
    @Modifying
    @Query("UPDATE Project p SET p.client.id = :clientId WHERE p.id = :projectId")
    void updateClientById(@Param("projectId") Long projectId, @Param("clientId") Long clientId);

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
