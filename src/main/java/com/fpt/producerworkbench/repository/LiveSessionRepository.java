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

    @Query("SELECT CASE WHEN COUNT(s) > 0 THEN true ELSE false END " +
            "FROM LiveSession s WHERE s.project.id = :projectId " +
            "AND s.status IN ('SCHEDULED', 'ACTIVE', 'PAUSED')")
    boolean hasBlockingSessionForProject(@Param("projectId") Long projectId);

}
