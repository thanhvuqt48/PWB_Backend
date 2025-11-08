package com.fpt.producerworkbench.repository;

import com.fpt.producerworkbench.common.SessionStatus;
import com.fpt.producerworkbench.entity.LiveSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface LiveSessionRepository extends JpaRepository<LiveSession, String> {



    @Query("SELECT s FROM LiveSession s WHERE s.project.id = :projectId")
    Page<LiveSession> findByProjectId(@Param("projectId") Long projectId, Pageable pageable);


    @Query("SELECT s FROM LiveSession s WHERE s.project.id = :projectId AND s.status = :status")
    Page<LiveSession> findByProjectIdAndStatus(
            @Param("projectId") Long projectId,
            @Param("status") SessionStatus status,
            Pageable pageable
    );

    @Query("SELECT s FROM LiveSession s WHERE s.host.id = :hostId")
    Page<LiveSession> findByHostId(@Param("hostId") Long hostId, Pageable pageable);

    @Query("SELECT CASE WHEN COUNT(s) > 0 THEN true ELSE false END " +
            "FROM LiveSession s WHERE s.project.id = :projectId AND s.status = 'ACTIVE'")
    boolean hasActiveSessionForProject(@Param("projectId") Long projectId);

    @Query("SELECT COUNT(s) FROM LiveSession s WHERE s.project.id = :projectId " +
            "AND s.status IN ('SCHEDULED', 'ACTIVE')")
    long countActiveSessionsForProject(@Param("projectId") Long projectId);

    @Query("SELECT s FROM LiveSession s WHERE s.scheduledStart IS NOT NULL " +
            "AND s.scheduledStart BETWEEN :startTime AND :endTime " +
            "AND s.status = 'SCHEDULED'")
    List<LiveSession> findSessionsToRemind(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );

    /**
     * Find sessions visible to a specific user in a project.
     * A session is visible if:
     * 1. User is the host (h.id = :userId)
     * 2. User is invited (exists in SessionParticipant)
     * 3. Session is public (s.isPublic = true)
     */
    @Query("SELECT DISTINCT s FROM LiveSession s " +
            "LEFT JOIN s.host h " +
            "LEFT JOIN SessionParticipant sp ON sp.session.id = s.id AND sp.user.id = :userId " +
            "WHERE s.project.id = :projectId " +
            "AND (h.id = :userId " +
            "     OR sp.id IS NOT NULL " +
            "     OR s.isPublic = true)")
    Page<LiveSession> findSessionsVisibleToUser(
            @Param("projectId") Long projectId,
            @Param("userId") Long userId,
            Pageable pageable
    );

    /**
     * Find sessions visible to a specific user in a project with status filter.
     */
    @Query("SELECT DISTINCT s FROM LiveSession s " +
            "LEFT JOIN s.host h " +
            "LEFT JOIN SessionParticipant sp ON sp.session.id = s.id AND sp.user.id = :userId " +
            "WHERE s.project.id = :projectId " +
            "AND s.status = :status " +
            "AND (h.id = :userId " +
            "     OR sp.id IS NOT NULL " +
            "     OR s.isPublic = true)")
    Page<LiveSession> findSessionsVisibleToUserByStatus(
            @Param("projectId") Long projectId,
            @Param("userId") Long userId,
            @Param("status") SessionStatus status,
            Pageable pageable
    );

}
