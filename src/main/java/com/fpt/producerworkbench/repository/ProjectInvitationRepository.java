package com.fpt.producerworkbench.repository;

import com.fpt.producerworkbench.common.InvitationStatus;
import com.fpt.producerworkbench.entity.ProjectInvitation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProjectInvitationRepository extends JpaRepository<ProjectInvitation, Long> {
    Optional<ProjectInvitation> findByToken(String token);

    List<ProjectInvitation> findByProjectIdAndStatus(Long projectId, InvitationStatus status);

    List<ProjectInvitation> findByEmailAndStatus(String email, InvitationStatus status);

    Page<ProjectInvitation> findByEmail(String email, Pageable pageable);
    Page<ProjectInvitation> findByEmailAndStatus(String email, InvitationStatus status, Pageable pageable);

    Page<ProjectInvitation> findByProjectCreatorEmail(String ownerEmail, Pageable pageable);
    Page<ProjectInvitation> findByProjectCreatorEmailAndStatus(String ownerEmail, InvitationStatus status, Pageable pageable);

    List<ProjectInvitation> findByProjectIdAndEmailAndStatus(Long projectId, String email, InvitationStatus status);

    @Modifying
    @Query("update ProjectInvitation pi set pi.status = com.fpt.producerworkbench.common.InvitationStatus.EXPIRED where pi.project.id = :projectId and pi.email = :email and pi.status = com.fpt.producerworkbench.common.InvitationStatus.PENDING")
    int expirePendingInvitationsForEmail(@Param("projectId") Long projectId, @Param("email") String email);

    @Modifying
    @Query("update ProjectInvitation pi set pi.status = com.fpt.producerworkbench.common.InvitationStatus.EXPIRED where pi.status = com.fpt.producerworkbench.common.InvitationStatus.PENDING and pi.expiresAt < CURRENT_TIMESTAMP")
    int expireAllPastDuePendingInvitations();

    Page<ProjectInvitation> findByEmailAndStatusAndExpiresAtAfter(String email, InvitationStatus status, java.time.LocalDateTime now, Pageable pageable);
}