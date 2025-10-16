package com.fpt.producerworkbench.repository;

import com.fpt.producerworkbench.entity.ProjectMember;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProjectMemberRepository extends JpaRepository<ProjectMember, Long> {
    List<ProjectMember> findByProjectId(Long projectId);

    long countByProjectIdAndProjectRoleAndAnonymousTrue(Long projectId, com.fpt.producerworkbench.common.ProjectRole role);

    Page<ProjectMember> findByProjectId(Long projectId, Pageable pageable);

    @Query("select pm from ProjectMember pm where pm.project.id = :projectId and (pm.projectRole <> com.fpt.producerworkbench.common.ProjectRole.COLLABORATOR or pm.anonymous = false)")
    Page<ProjectMember> findVisibleForNonOwner(@Param("projectId") Long projectId, Pageable pageable);

    @Query("select pm from ProjectMember pm where pm.project.id = :projectId and (pm.projectRole <> com.fpt.producerworkbench.common.ProjectRole.COLLABORATOR or pm.anonymous = false) and pm.projectRole <> com.fpt.producerworkbench.common.ProjectRole.CLIENT")
    Page<ProjectMember> findVisibleForAnonymousCollaborator(@Param("projectId") Long projectId, Pageable pageable);

    Optional<ProjectMember> findByProjectIdAndUserEmail(Long projectId, String email);

    @Query("SELECT pm FROM ProjectMember pm " +
            "WHERE pm.project.id = :projectId AND pm.user.id = :userId")
    Optional<ProjectMember> findByProjectIdAndUserId(
            @Param("projectId") Long projectId,
            @Param("userId") Long userId
    );

    @Query("SELECT CASE WHEN COUNT(pm) > 0 THEN true ELSE false END " +
            "FROM ProjectMember pm " +
            "WHERE pm.project.id = :projectId AND pm.user.id = :userId")
    boolean existsByProjectIdAndUserId(
            @Param("projectId") Long projectId,
            @Param("userId") Long userId
    );
}