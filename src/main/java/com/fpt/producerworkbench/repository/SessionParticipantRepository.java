package com.fpt.producerworkbench.repository;

import com.fpt.producerworkbench.common.InvitationStatus;
import com.fpt.producerworkbench.common.ParticipantRole;
import com.fpt.producerworkbench.entity.SessionParticipant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SessionParticipantRepository extends JpaRepository<SessionParticipant, Long> {

    /**
     * Find participant by session ID and user ID
     */
    @Query("SELECT p FROM SessionParticipant p " +
            "WHERE p.session.id = :sessionId AND p.user.id = :userId")
    Optional<SessionParticipant> findBySessionIdAndUserId(
            @Param("sessionId") String sessionId,
            @Param("userId") Long userId
    );

    /**
     * Find all participants in a session
     */
    @Query("SELECT p FROM SessionParticipant p WHERE p.session.id = :sessionId ORDER BY p.createdAt ASC")
    List<SessionParticipant> findBySessionId(@Param("sessionId") String sessionId);

    /**
     * Find all online participants in a session
     */
    @Query("SELECT p FROM SessionParticipant p " +
            "WHERE p.session.id = :sessionId AND p.isOnline = true ORDER BY p.joinedAt ASC")
    List<SessionParticipant> findOnlineParticipantsBySessionId(@Param("sessionId") String sessionId);

    /**
     * Find participants by invitation status
     */
    @Query("SELECT p FROM SessionParticipant p " +
            "WHERE p.session.id = :sessionId AND p.invitationStatus = :status")
    List<SessionParticipant> findBySessionIdAndInvitationStatus(
            @Param("sessionId") String sessionId,
            @Param("status") InvitationStatus status
    );

    /**
     * Find all sessions where user is a participant
     */
    @Query("SELECT p FROM SessionParticipant p WHERE p.user.id = :userId ORDER BY p.createdAt DESC")
    List<SessionParticipant> findByUserId(@Param("userId") Long userId);

    /**
     * Find participant by role
     */
    @Query("SELECT p FROM SessionParticipant p " +
            "WHERE p.session.id = :sessionId AND p.participantRole = :role")
    List<SessionParticipant> findBySessionIdAndRole(
            @Param("sessionId") String sessionId,
            @Param("role") ParticipantRole role
    );

    /**
     * Find session host (OWNER role)
     */
    @Query("SELECT p FROM SessionParticipant p " +
            "WHERE p.session.id = :sessionId AND p.participantRole = 'OWNER'")
    Optional<SessionParticipant> findHostBySessionId(@Param("sessionId") String sessionId);

    /**
     * Count participants in a session
     */
    @Query("SELECT COUNT(p) FROM SessionParticipant p WHERE p.session.id = :sessionId")
    Long countBySessionId(@Param("sessionId") String sessionId);

    /**
     * Count online participants in a session
     */
    @Query("SELECT COUNT(p) FROM SessionParticipant p " +
            "WHERE p.session.id = :sessionId AND p.isOnline = true")
    Long countOnlineBySessionId(@Param("sessionId") String sessionId);

    /**
     * Check if user is already in session
     */
    @Query("SELECT CASE WHEN COUNT(p) > 0 THEN true ELSE false END " +
            "FROM SessionParticipant p WHERE p.session.id = :sessionId AND p.user.id = :userId")
    boolean existsBySessionIdAndUserId(
            @Param("sessionId") String sessionId,
            @Param("userId") Long userId
    );

    /**
     * Find participants with expired tokens (for token refresh)
     */
    @Query("SELECT p FROM SessionParticipant p " +
            "WHERE p.session.id = :sessionId " +
            "AND p.isOnline = true " +
            "AND p.agoraTokenExpiresAt < CURRENT_TIMESTAMP")
    List<SessionParticipant> findParticipantsWithExpiredTokens(@Param("sessionId") String sessionId);

    /**
     * Delete all participants of a session (for cleanup)
     */
    void deleteBySessionId(String sessionId);
}