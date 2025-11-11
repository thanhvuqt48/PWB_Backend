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
    
    // Role information
    private RoleInfo role;
    
    // Project permissions
    private ProjectPermissions project;
    
    // Milestone permissions
    private MilestonePermissions milestone;
    
    // Contract permissions
    private ContractPermissions contract;
    
    // Payment permissions (thanh toán theo hợp đồng)
    private PaymentPermissions payment;
    
    // Money Split permissions (phân chia tiền milestone)
    private MoneySplitPermissions moneySplit;
    
    // Expense permissions (chi phí milestone)
    private ExpensePermissions expense;
    
    private String reason;
    
    // Nested classes for grouped permissions
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RoleInfo {
        private UserRole userRole;
        private ProjectRole projectRole;
        private boolean anonymous;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProjectPermissions {
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
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MilestonePermissions {
        private boolean canCreateMilestone;
        private boolean canViewMilestones;
        private boolean canEditMilestone;
        private boolean canDeleteMilestone;
        private boolean canAddMembersToMilestone;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ContractPermissions {
        private boolean canCreateContract;
        private boolean canViewContract;
        private boolean canInviteToSign;
        private boolean canDeclineContract;
        private boolean canEditContract;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentPermissions {
        private boolean canCreatePayment;
        private boolean canViewPayment;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MoneySplitPermissions {
        private boolean canCreateMoneySplit;
        private boolean canUpdateMoneySplit;
        private boolean canDeleteMoneySplit;
        private boolean canApproveMoneySplit;
        private boolean canRejectMoneySplit;
        private boolean canViewMoneySplit;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExpensePermissions {
        private boolean canCreateExpense;
        private boolean canUpdateExpense;
        private boolean canDeleteExpense;
    }
}
