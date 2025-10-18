package com.fpt.producerworkbench.dto.response;

import com.fpt.producerworkbench.common.ProjectRole;
import com.fpt.producerworkbench.common.UserRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectPermissionResponse {
    
    private boolean canCreateProject;
    private boolean canInviteMembers;
    private boolean canViewProject;
    private boolean canEditProject;
    private boolean canDeleteProject;
    private boolean canViewMembers;
    private boolean canManageInvitations;
    private boolean canAcceptInvitation;
    private boolean canDeclineInvitation;
    private boolean canViewMyInvitations;
    
    private UserRole userRole;
    private ProjectRole projectRole;
    private boolean isProjectOwner;
    private boolean isProjectMember;
    private String reason; // Lý do không được phép (nếu có)
    
    // Helper methods for FE
    public boolean isProducer() {
        return userRole == UserRole.PRODUCER;
    }
    
    public boolean isAdmin() {
        return userRole == UserRole.ADMIN;
    }
    
    public boolean isCustomer() {
        return userRole == UserRole.CUSTOMER;
    }
    
    public boolean isOwner() {
        return isProjectOwner;
    }
    
    public boolean isClient() {
        return projectRole == ProjectRole.CLIENT;
    }
    
    public boolean isCollaborator() {
        return projectRole == ProjectRole.COLLABORATOR;
    }
    
    public boolean isObserver() {
        return projectRole == ProjectRole.OBSERVER;
    }
}
