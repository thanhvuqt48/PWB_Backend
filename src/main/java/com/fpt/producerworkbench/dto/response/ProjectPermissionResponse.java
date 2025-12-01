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
    
    // Room permissions
    private RoomPermissions room;
    
    // Milestone permissions
    private MilestonePermissions milestone;
    
    // Contract permissions
    private ContractPermissions contract;
    
    // Addendum permissions (phụ lục hợp đồng)
    private AddendumPermissions addendum;
    
    // Payment permissions (thanh toán theo hợp đồng)
    private PaymentPermissions payment;
    
    // Money Split permissions (phân chia tiền milestone)
    private MoneySplitPermissions moneySplit;
    
    // Expense permissions (chi phí milestone)
    private ExpensePermissions expense;
    
    // Track permissions (sản phẩm nhạc trong phòng nội bộ)
    private TrackPermissions track;
    
    // Client Delivery permissions (gửi sản phẩm cho khách hàng)
    private ClientDeliveryPermissions clientDelivery;
    
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
        private boolean canRemoveMembers;
        private boolean canUpdateMemberRole;
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
    public static class RoomPermissions {
        private boolean canEnterCustomerRoom;
        private boolean canEnterInternalRoom;
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
        private boolean canRemoveMembersFromMilestone;
        private boolean canCompleteMilestone; // Chỉ CLIENT mới có quyền chấp nhận hoàn thành cột mốc
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
    public static class AddendumPermissions {
        private boolean canCreateAddendum;
        private boolean canViewAddendum;
        private boolean canInviteToSign;
        private boolean canDeclineAddendum;
        private boolean canEditAddendum;
        private boolean canCreateAddendumPayment; // Thanh toán phụ lục
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
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrackPermissions {
        private boolean canUploadTrack;
        private boolean canViewTrack;
        private boolean canUpdateTrack;
        private boolean canDeleteTrack;
        private boolean canPlayTrack;
        private boolean canApproveTrackStatus; // Chỉ chủ dự án mới có quyền phê duyệt/từ chối trạng thái track
        private boolean canDownloadTrack; // Chỉ chủ dự án hoặc milestone members được chỉ định mới có quyền download
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClientDeliveryPermissions {
        private boolean canSendTrackToClient;           // Gửi track cho client (Owner only)
        private boolean canViewClientTracks;            // Xem tracks trong Client Room (Owner, Admin, Client, Observer nếu funded)
        private boolean canAcceptDelivery;               // Chấp nhận sản phẩm - status = ACCEPTED (Client, Observer, Owner nếu funded)
        private boolean canRejectDelivery;              // Từ chối sản phẩm - status = REJECTED (Client, Observer nếu funded)
        private boolean canRequestEditDelivery;         // Yêu cầu chỉnh sửa - status = REQUEST_EDIT (Client, Observer nếu funded)
        private boolean canViewProductCountRemaining;   // Xem số lượt gửi còn lại (Owner, Admin, Client, Observer nếu funded)
        private boolean canCancelDelivery;              // Hủy delivery (Owner only)
        // Client Room Comment permissions
        private boolean canCreateClientRoomComment;     // Tạo comment trong Client Room (Owner, Admin, Client, Observer nếu funded)
        private boolean canViewClientRoomComments;      // Xem comment trong Client Room (Owner, Admin, Client, Observer nếu funded)
        private boolean canUpdateClientRoomComment;     // Sửa comment trong Client Room (chỉ comment owner, Owner, Admin, Client, Observer nếu funded)
        private boolean canDeleteClientRoomComment;     // Xóa comment trong Client Room (comment owner hoặc track owner, Owner, Admin, Client, Observer nếu funded)
        private boolean canUpdateClientRoomCommentStatus; // Cập nhật status comment trong Client Room (chỉ Owner)
    }
}
