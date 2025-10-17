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

    /**
     * Find session by Agora channel name
     */
    Optional<LiveSession> findByAgoraChannelName(String agoraChannelName);

    /**
     * Find all sessions by project ID
     */
    @Query("SELECT s FROM LiveSession s WHERE s.project.id = :projectId")
    Page<LiveSession> findByProjectId(@Param("projectId") Long projectId, Pageable pageable);

    /**
     * Find sessions by project ID and status
     */
    @Query("SELECT s FROM LiveSession s WHERE s.project.id = :projectId AND s.status = :status")
    Page<LiveSession> findByProjectIdAndStatus(
            @Param("projectId") Long projectId,
            @Param("status") SessionStatus status,
            Pageable pageable
    );

    /**
     * Find all sessions hosted by user
     */
    @Query("SELECT s FROM LiveSession s WHERE s.host.id = :hostId")
    Page<LiveSession> findByHostId(@Param("hostId") Long hostId, Pageable pageable);

    /**
     * Find all demo sessions (for testing)
     */
    @Query("SELECT s FROM LiveSession s WHERE s.demoScenario IS NOT NULL ORDER BY s.createdAt DESC")
    List<LiveSession> findDemoSessions();

    /**
     * Find active sessions
     */
    @Query("SELECT s FROM LiveSession s WHERE s.status = 'ACTIVE' ORDER BY s.actualStart DESC")
    List<LiveSession> findActiveSessions();

    /**
     * Find scheduled sessions
     */
    @Query("SELECT s FROM LiveSession s WHERE s.status = 'SCHEDULED' ORDER BY s.scheduledStart ASC")
    List<LiveSession> findScheduledSessions();

    /**
     * Find sessions ready to auto-start (scheduled start within threshold)
     */
    @Query("SELECT s FROM LiveSession s WHERE s.status = 'SCHEDULED' " +
            "AND s.scheduledStart BETWEEN :now AND :threshold")
    List<LiveSession> findSessionsReadyToStart(
            @Param("now") LocalDateTime now,
            @Param("threshold") LocalDateTime threshold
    );

    /**
     * Find active sessions with no participants (for auto-end)
     */
    @Query("SELECT s FROM LiveSession s WHERE s.status = 'ACTIVE' " +
            "AND s.currentParticipants = 0 " +
            "AND s.actualStart < :threshold")
    List<LiveSession> findEmptyActiveSessions(@Param("threshold") LocalDateTime threshold);

    /**
     * Find paused sessions for too long (for auto-end)
     */
    @Query("SELECT s FROM LiveSession s WHERE s.status = 'PAUSED' " +
            "AND s.updatedAt < :threshold")
    List<LiveSession> findLongPausedSessions(@Param("threshold") LocalDateTime threshold);

    /**
     * Check if project has active session
     */
    @Query("SELECT CASE WHEN COUNT(s) > 0 THEN true ELSE false END " +
            "FROM LiveSession s WHERE s.project.id = :projectId AND s.status = 'ACTIVE'")
    boolean hasActiveSessionForProject(@Param("projectId") Long projectId);

    /**
     * Count sessions by project
     */
    @Query("SELECT COUNT(s) FROM LiveSession s WHERE s.project.id = :projectId")
    Long countByProjectId(@Param("projectId") Long projectId);

    /**
     * Count sessions by project and status
     */
    @Query("SELECT COUNT(s) FROM LiveSession s WHERE s.project.id = :projectId AND s.status = :status")
    Long countByProjectIdAndStatus(
            @Param("projectId") Long projectId,
            @Param("status") SessionStatus status
    );
}
