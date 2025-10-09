package com.fpt.producerworkbench.service;

import com.fpt.producerworkbench.dto.request.InvitationRequest;
import com.fpt.producerworkbench.dto.response.InvitationResponse;
import com.fpt.producerworkbench.entity.User;

import java.util.List;

public interface InvitationService {
    String createInvitation(Long projectId, InvitationRequest request, User inviter);
    void acceptInvitation(String token, User acceptingUser);

    List<InvitationResponse> getPendingInvitationsForProject(Long projectId, User currentUser);
    void cancelInvitation(Long invitationId, User currentUser);

    List<InvitationResponse> getMyPendingInvitations(User currentUser);
    void declineInvitation(Long invitationId, User currentUser);
}