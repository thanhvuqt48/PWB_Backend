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


    @Query("SELECT p FROM SessionParticipant p " +
            "WHERE p.session.id = :sessionId AND p.user.id = :userId")
    Optional<SessionParticipant> findBySessionIdAndUserId(
            @Param("sessionId") String sessionId,
            @Param("userId") Long userId
    );

    @Query("SELECT p FROM SessionParticipant p WHERE p.session.id = :sessionId ORDER BY p.createdAt ASC")
    List<SessionParticipant> findBySessionId(@Param("sessionId") String sessionId);

    @Query("SELECT p FROM SessionParticipant p " +
            "WHERE p.session.id = :sessionId AND p.isOnline = true ORDER BY p.joinedAt ASC")
    List<SessionParticipant> findOnlineParticipantsBySessionId(@Param("sessionId") String sessionId);



    @Query("SELECT COUNT(p) FROM SessionParticipant p WHERE p.session.id = :sessionId")
    Long countBySessionId(@Param("sessionId") String sessionId);

    @Query("SELECT CASE WHEN COUNT(p) > 0 THEN true ELSE false END " +
            "FROM SessionParticipant p WHERE p.session.id = :sessionId AND p.user.id = :userId")
    boolean existsBySessionIdAndUserId(
            @Param("sessionId") String sessionId,
            @Param("userId") Long userId
    );
}