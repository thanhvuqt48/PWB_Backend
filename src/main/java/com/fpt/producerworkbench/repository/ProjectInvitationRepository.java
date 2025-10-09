package com.fpt.producerworkbench.repository;

import com.fpt.producerworkbench.common.InvitationStatus;
import com.fpt.producerworkbench.entity.ProjectInvitation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProjectInvitationRepository extends JpaRepository<ProjectInvitation, Long> {
    Optional<ProjectInvitation> findByToken(String token);

    List<ProjectInvitation> findByProjectIdAndStatus(Long projectId, InvitationStatus status);

    List<ProjectInvitation> findByEmailAndStatus(String email, InvitationStatus status);
}