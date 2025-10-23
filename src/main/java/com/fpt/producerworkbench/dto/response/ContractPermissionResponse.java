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
public class ContractPermissionResponse {
    
    private boolean canCreateContract;
    private boolean canViewContract;
    private boolean canInviteToSign;
    private boolean canDeclineContract;
    private boolean canEditContract;
    
    private UserRole userRole;
    private ProjectRole projectRole;
    private String reason; // Lý do không được phép (nếu có)
    
    // Helper methods for FE
    public boolean isProducer() {
        return userRole == UserRole.PRODUCER;
    }
    
    public boolean isAdmin() {
        return userRole == UserRole.ADMIN;
    }
    
    public boolean isOwner() {
        return projectRole == ProjectRole.OWNER;
    }
    
    public boolean isClient() {
        return projectRole == ProjectRole.CLIENT;
    }
}
