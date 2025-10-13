package com.fpt.producerworkbench.service;

import com.fpt.producerworkbench.common.InvitationStatus;
import com.fpt.producerworkbench.dto.request.InvitationRequest;
import com.fpt.producerworkbench.dto.response.InvitationResponse;
import com.fpt.producerworkbench.entity.User;
import com.fpt.producerworkbench.dto.response.PageResponse;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface InvitationService {
    String createInvitation(Long projectId, InvitationRequest request, User inviter);
    void acceptInvitation(String token, User acceptingUser);
    void acceptInvitationById(Long invitationId, User acceptingUser);

    List<InvitationResponse> getPendingInvitationsForProject(Long projectId, User currentUser);
    void cancelInvitation(Long invitationId, User currentUser);

    List<InvitationResponse> getMyPendingInvitations(User currentUser);
    PageResponse<InvitationResponse> getMyPendingInvitationsPage(User currentUser, Pageable pageable);
    void declineInvitation(Long invitationId, User currentUser);

    PageResponse<InvitationResponse> getAllMyInvitations(User currentUser, InvitationStatus status, Pageable pageable);
    PageResponse<InvitationResponse> getAllOwnedInvitations(User currentUser, InvitationStatus status, Pageable pageable);
}