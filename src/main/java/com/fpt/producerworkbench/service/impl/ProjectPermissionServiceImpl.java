package com.fpt.producerworkbench.service.impl;

import com.fpt.producerworkbench.common.MoneySplitStatus;
import com.fpt.producerworkbench.common.ProjectRole;
import com.fpt.producerworkbench.common.UserRole;
import com.fpt.producerworkbench.dto.response.ContractPermissionResponse;
import com.fpt.producerworkbench.dto.response.MilestonePermissionResponse;
import com.fpt.producerworkbench.dto.response.ProjectPermissionResponse;
import com.fpt.producerworkbench.entity.Project;
import com.fpt.producerworkbench.entity.Milestone;
import com.fpt.producerworkbench.entity.ProjectMember;
import com.fpt.producerworkbench.entity.User;
import com.fpt.producerworkbench.exception.AppException;
import com.fpt.producerworkbench.exception.ErrorCode;
import com.fpt.producerworkbench.repository.ProjectMemberRepository;
import com.fpt.producerworkbench.repository.ProjectRepository;
import com.fpt.producerworkbench.repository.UserRepository;
import com.fpt.producerworkbench.repository.MilestoneMoneySplitRepository;
import com.fpt.producerworkbench.repository.MilestoneRepository;
import com.fpt.producerworkbench.service.ProjectPermissionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProjectPermissionServiceImpl implements ProjectPermissionService {

    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final MilestoneRepository milestoneRepository;
    private final MilestoneMoneySplitRepository milestoneMoneySplitRepository;

    /**
     * Context class chứa thông tin chung về user và project để tránh query database nhiều lần
     */
    private static class ProjectContext {
        final Long userId;
        final UserRole userRole;
        final boolean isProjectOwner;
        final ProjectRole projectRole;
        final boolean isProjectMember;
        final boolean isAnonymousMember;
        final Project project;

        ProjectContext(Long userId, UserRole userRole, boolean isProjectOwner, ProjectRole role,
                       boolean isProjectMember, boolean isAnonymousMember, Project project) {
            this.userId = userId;
            this.userRole = userRole;
            this.isProjectOwner = isProjectOwner;
            this.projectRole = role;
            this.isProjectMember = isProjectMember;
            this.isAnonymousMember = isAnonymousMember;
            this.project = project;
        }
    }

    /**
     * Kiểm tra authentication có hợp lệ không
     */
    private boolean isValidAuthentication(Authentication auth) {
        return auth != null && auth.getName() != null && !auth.getName().isBlank();
    }

    /**
     * Load user từ authentication
     */
    private User loadUser(Authentication auth) {
        return userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
    }

    /**
     * Load project context (user, project, projectRole, isProjectOwner) để tránh query database nhiều lần
     */
    private ProjectContext loadProjectContext(Authentication auth, Long projectId) {
        if (!isValidAuthentication(auth)) {
            return null;
        }

        User user = loadUser(auth);
        UserRole userRole = user.getRole();

        if (projectId == null) {
            return null;
        }

        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new AppException(ErrorCode.PROJECT_NOT_FOUND));

        boolean isProjectOwner = project.getCreator() != null &&
                user.getId().equals(project.getCreator().getId());

        Optional<ProjectMember> memberOpt = projectMemberRepository
                .findByProjectIdAndUserEmail(projectId, user.getEmail());

        ProjectRole projectRole = null;
        boolean isProjectMember = false;
        boolean isAnonymousMember = false;
        if (isProjectOwner) {
            projectRole = ProjectRole.OWNER;
            isProjectMember = true;
            isAnonymousMember = false;
        } else if (memberOpt.isPresent()) {
            ProjectMember member = memberOpt.get();
            projectRole = member.getProjectRole();
            isProjectMember = true;
            isAnonymousMember = member.isAnonymous();
        }

        return new ProjectContext(user.getId(), userRole, isProjectOwner, projectRole, isProjectMember, isAnonymousMember, project);
    }

    /**
     * Tạo default response với tất cả quyền = false
     */
    private ProjectPermissionResponse createDefaultDeniedResponse(String reason) {
        return ProjectPermissionResponse.builder()
                .role(ProjectPermissionResponse.RoleInfo.builder()
                        .userRole(null)
                        .projectRole(null)
                        .anonymous(false)
                        .build())
                .project(ProjectPermissionResponse.ProjectPermissions.builder()
                        .canCreateProject(false)
                        .canInviteMembers(false)
                        .canRemoveMembers(false)
                        .canUpdateMemberRole(false)
                        .canViewProject(false)
                        .canEditProject(false)
                        .canDeleteProject(false)
                        .canViewMembers(false)
                        .canManageInvitations(false)
                        .canAcceptInvitation(false)
                        .canDeclineInvitation(false)
                        .canViewMyInvitations(false)
                        .build())
                .room(ProjectPermissionResponse.RoomPermissions.builder()
                        .canEnterCustomerRoom(false)
                        .canEnterInternalRoom(false)
                        .build())
                .milestone(ProjectPermissionResponse.MilestonePermissions.builder()
                        .canCreateMilestone(false)
                        .canViewMilestones(false)
                        .canEditMilestone(false)
                        .canDeleteMilestone(false)
                        .canAddMembersToMilestone(false)
                        .canRemoveMembersFromMilestone(false)
                        .canCompleteMilestone(false)
                        .build())
                .contract(ProjectPermissionResponse.ContractPermissions.builder()
                        .canCreateContract(false)
                        .canViewContract(false)
                        .canInviteToSign(false)
                        .canDeclineContract(false)
                        .canEditContract(false)
                        .build())
                .payment(ProjectPermissionResponse.PaymentPermissions.builder()
                        .canCreatePayment(false)
                        .canViewPayment(false)
                        .build())
                .moneySplit(ProjectPermissionResponse.MoneySplitPermissions.builder()
                        .canCreateMoneySplit(false)
                        .canUpdateMoneySplit(false)
                        .canDeleteMoneySplit(false)
                        .canApproveMoneySplit(false)
                        .canRejectMoneySplit(false)
                        .canViewMoneySplit(false)
                        .build())
                .expense(ProjectPermissionResponse.ExpensePermissions.builder()
                        .canCreateExpense(false)
                        .canUpdateExpense(false)
                        .canDeleteExpense(false)
                        .build())
                .track(ProjectPermissionResponse.TrackPermissions.builder()
                        .canUploadTrack(false)
                        .canViewTrack(false)
                        .canUpdateTrack(false)
                        .canDeleteTrack(false)
                        .canPlayTrack(false)
                        .canApproveTrackStatus(false)
                        .build())
                .clientDelivery(ProjectPermissionResponse.ClientDeliveryPermissions.builder()
                        .canSendTrackToClient(false)
                        .canViewClientTracks(false)
                        .canAcceptDelivery(false)
                        .canRejectDelivery(false)
                        .canRequestEditDelivery(false)
                        .canViewProductCountRemaining(false)
                        .canCancelDelivery(false)
                        .canCreateClientRoomComment(false)
                        .canViewClientRoomComments(false)
                        .canUpdateClientRoomComment(false)
                        .canDeleteClientRoomComment(false)
                        .canUpdateClientRoomCommentStatus(false)
                        .build())
                .reason(reason)
                .build();
    }

    @Override
    public ProjectPermissionResponse checkProjectPermissions(Authentication auth, Long projectId) {
        if (!isValidAuthentication(auth)) {
            return createDefaultDeniedResponse("Chưa đăng nhập");
        }

        if (projectId == null) {
            throw new AppException(ErrorCode.BAD_REQUEST, "projectId không được null.");
        }

        ProjectContext context = loadProjectContext(auth, projectId);
        if (context == null) {
            return createDefaultDeniedResponse("Không thể load project context");
        }

        boolean canEnterCustomerRoom = canEnterCustomerRoom(context);
        boolean canEnterInternalRoom = canEnterInternalRoom(context);

        boolean hasApprovedMoneySplit = hasApprovedMoneySplit(context.project, context.userId);

        return ProjectPermissionResponse.builder()
                .role(ProjectPermissionResponse.RoleInfo.builder()
                        .userRole(context.userRole)
                        .projectRole(context.projectRole)
                        .anonymous(context.isAnonymousMember)
                        .build())

                .project(ProjectPermissionResponse.ProjectPermissions.builder()
                        .canCreateProject(canCreateProject(context.userRole))
                        .canInviteMembers(canInviteMembers(context.userRole, context.isProjectOwner))
                        .canRemoveMembers(canRemoveProjectMembers(context.isProjectOwner))
                        .canUpdateMemberRole(canUpdateProjectMemberRole(context.isProjectOwner))
                        .canViewProject(canViewProject(context.userRole, context.isProjectOwner, context.projectRole))
                        .canEditProject(canEditProject(context.userRole, context.isProjectOwner))
                        .canDeleteProject(canDeleteProject(context.userRole, context.isProjectOwner))
                        .canViewMembers(canViewMembers(context.userRole, context.isProjectOwner, context.projectRole))
                        .canManageInvitations(canManageInvitations(context.userRole, context.isProjectOwner))
                        .canAcceptInvitation(true)
                        .canDeclineInvitation(true)
                        .canViewMyInvitations(true)
                        .build())

                .room(ProjectPermissionResponse.RoomPermissions.builder()
                        .canEnterCustomerRoom(canEnterCustomerRoom)
                        .canEnterInternalRoom(canEnterInternalRoom)
                        .build())

                // Milestone permissions
                .milestone(ProjectPermissionResponse.MilestonePermissions.builder()
                        .canCreateMilestone(canCreateMilestone(context.userRole, context.projectRole, context.isProjectOwner))
                        .canViewMilestones(canViewMilestones(context.userRole, context.projectRole, context.isProjectOwner))
                        .canEditMilestone(canEditMilestone(context.userRole, context.projectRole, context.isProjectOwner))
                        .canDeleteMilestone(canDeleteMilestone(context.userRole, context.projectRole, context.isProjectOwner))
                        .canAddMembersToMilestone(canAddMembersToMilestone(context.userRole, context.projectRole, context.isProjectOwner))
                        .canRemoveMembersFromMilestone(canRemoveMembersFromMilestone(context.userRole, context.projectRole, context.isProjectOwner))
                        .canCompleteMilestone(canCompleteMilestone(context.projectRole, context.project))
                        .build())

                // Contract permissions
                .contract(ProjectPermissionResponse.ContractPermissions.builder()
                        .canCreateContract(canCreateContract(context.userRole, context.projectRole, context.isProjectOwner))
                        .canViewContract(canViewContract(context.userRole, context.projectRole, context.isProjectOwner))
                        .canInviteToSign(canInviteToSign(context.userRole, context.projectRole, context.isProjectOwner))
                        .canDeclineContract(canDeclineContract(context.userRole, context.projectRole, context.isProjectOwner))
                        .canEditContract(canEditContract(context.userRole, context.projectRole, context.isProjectOwner))
                        .build())

                // Payment permissions
                .payment(ProjectPermissionResponse.PaymentPermissions.builder()
                        .canCreatePayment(canCreatePayment(context.userRole, context.projectRole))
                        .canViewPayment(canViewPayment(context.userRole, context.isProjectOwner, context.projectRole))
                        .build())

                // Money Split permissions
                .moneySplit(ProjectPermissionResponse.MoneySplitPermissions.builder()
                        .canCreateMoneySplit(canCreateMoneySplit(context.userRole, context.projectRole, context.isProjectOwner))
                        .canUpdateMoneySplit(canUpdateMoneySplit(context.userRole, context.projectRole, context.isProjectOwner))
                        .canDeleteMoneySplit(canDeleteMoneySplit(context.userRole, context.projectRole, context.isProjectOwner))
                        .canApproveMoneySplit(canApproveMoneySplit(context.userRole, context.projectRole, context.isProjectMember))
                        .canRejectMoneySplit(canRejectMoneySplit(context.userRole, context.projectRole, context.isProjectMember))
                        .canViewMoneySplit(canViewMoneySplit(context.userRole, context.isProjectOwner, context.projectRole))
                        .build())

                // Expense permissions
                .expense(ProjectPermissionResponse.ExpensePermissions.builder()
                        .canCreateExpense(canCreateExpense(context.userRole, context.projectRole, context.isProjectOwner))
                        .canUpdateExpense(canUpdateExpense(context.userRole, context.projectRole, context.isProjectOwner))
                        .canDeleteExpense(canDeleteExpense(context.userRole, context.projectRole, context.isProjectOwner))
                        .build())

                // Track permissions - NOW aware of MoneySplit
                .track(ProjectPermissionResponse.TrackPermissions.builder()
                        .canUploadTrack(canUploadTrack(context.userRole, context.projectRole, context.isProjectOwner, hasApprovedMoneySplit))
                        .canViewTrack(canViewTrack(context.userRole, context.projectRole, context.isProjectOwner, hasApprovedMoneySplit))
                        .canUpdateTrack(canUpdateTrack(context.userRole, context.projectRole, context.isProjectOwner, hasApprovedMoneySplit))
                        .canDeleteTrack(canDeleteTrack(context.userRole, context.projectRole, context.isProjectOwner, hasApprovedMoneySplit))
                        .canPlayTrack(canPlayTrack(context.userRole, context.projectRole, context.isProjectOwner, hasApprovedMoneySplit))
                        .canApproveTrackStatus(canApproveTrackStatus(context.isProjectOwner))
                        .build())

                // Client Delivery permissions
                .clientDelivery(ProjectPermissionResponse.ClientDeliveryPermissions.builder()
                        .canSendTrackToClient(canSendTrackToClient(context.isProjectOwner))
                        .canViewClientTracks(canViewClientTracks(context.userRole, context.isProjectOwner, context.projectRole, context.project))
                        .canAcceptDelivery(canAcceptDelivery(context.userRole, context.isProjectOwner, context.projectRole, context.project))
                        .canRejectDelivery(canRejectDelivery(context.projectRole, context.project))
                        .canRequestEditDelivery(canRequestEditDelivery(context.projectRole, context.project))
                        .canViewProductCountRemaining(canViewProductCountRemaining(context.userRole, context.isProjectOwner, context.projectRole, context.project))
                        .canCancelDelivery(canCancelDelivery(context.isProjectOwner))
                        .canCreateClientRoomComment(canCreateClientRoomComment(context.userRole, context.isProjectOwner, context.projectRole, context.project))
                        .canViewClientRoomComments(canViewClientRoomComments(context.userRole, context.isProjectOwner, context.projectRole, context.project))
                        .canUpdateClientRoomComment(canUpdateClientRoomComment(context.userRole, context.isProjectOwner, context.projectRole, context.project))
                        .canDeleteClientRoomComment(canDeleteClientRoomComment(context.userRole, context.isProjectOwner, context.projectRole, context.project))
                        .canUpdateClientRoomCommentStatus(canUpdateClientRoomCommentStatus(context.isProjectOwner))
                        .build())

                .reason(getPermissionReason(context.userRole, context.projectRole, context.isProjectOwner, context.isProjectMember))
                .build();
    }

    private boolean canCreateProject(UserRole userRole) {
        return userRole == UserRole.PRODUCER || userRole == UserRole.ADMIN;
    }

    private boolean canInviteMembers(UserRole userRole, boolean isProjectOwner) {
        return userRole == UserRole.ADMIN || isProjectOwner;
    }

    private boolean canRemoveProjectMembers(boolean isProjectOwner) {
        return isProjectOwner;
    }

    private boolean canUpdateProjectMemberRole(boolean isProjectOwner) {
        return isProjectOwner;
    }

    private boolean canViewProject(UserRole userRole, boolean isProjectOwner, ProjectRole projectRole) {
        return userRole == UserRole.ADMIN || isProjectOwner || projectRole != null;
    }

    private boolean canEditProject(UserRole userRole, boolean isProjectOwner) {
        return userRole == UserRole.ADMIN || isProjectOwner;
    }

    private boolean canDeleteProject(UserRole userRole, boolean isProjectOwner) {
        return userRole == UserRole.ADMIN || isProjectOwner;
    }

    private boolean canViewMembers(UserRole userRole, boolean isProjectOwner, ProjectRole projectRole) {
        return userRole == UserRole.ADMIN || isProjectOwner || projectRole != null;
    }

    private boolean canManageInvitations(UserRole userRole, boolean isProjectOwner) {
        return userRole == UserRole.ADMIN || isProjectOwner;
    }

    private String getPermissionReason(UserRole userRole, ProjectRole projectRole, boolean isProjectOwner, boolean isProjectMember) {
        if (userRole == null) {
            return "Người dùng không tồn tại";
        }
        
        if (!isProjectMember && !isProjectOwner && userRole != UserRole.ADMIN) {
            return "Bạn không phải là thành viên của dự án này";
        }
        
        return null;
    }

    private boolean canEnterCustomerRoom(ProjectContext context) {
        if (context == null) {
            return false;
        }

        if (context.userRole == UserRole.ADMIN || context.isProjectOwner) {
            return true;
        }

        Project project = context.project;
        if (project == null || !Boolean.TRUE.equals(project.getIsFunded())) {
            return false;
        }

        return context.projectRole == ProjectRole.CLIENT || context.projectRole == ProjectRole.OBSERVER;
    }

    private boolean canEnterInternalRoom(ProjectContext context) {
        if (context == null) {
            return false;
        }

        if (context.userRole == UserRole.ADMIN || context.isProjectOwner) {
            return true;
        }

        if (!context.isProjectMember || context.projectRole == null) {
            return false;
        }

        if (context.projectRole != ProjectRole.COLLABORATOR) {
            return false;
        }

        return hasApprovedMoneySplit(context.project, context.userId);
    }

    private boolean hasApprovedMoneySplit(Project project, Long userId) {
        if (project == null || userId == null) {
            return false;
        }

        List<Milestone> milestones = milestoneRepository.findByProjectIdOrderBySequenceAsc(project.getId());
        if (milestones.isEmpty()) {
            return false;
        }

        List<Long> milestoneIds = milestones.stream()
                .map(Milestone::getId)
                .collect(Collectors.toList());

        if (milestoneIds.isEmpty()) {
            return false;
        }

        return milestoneMoneySplitRepository.existsByMilestoneIdInAndUserIdAndStatus(
                milestoneIds,
                userId,
                MoneySplitStatus.APPROVED
        );
    }

    @Override
    public MilestonePermissionResponse checkMilestonePermissions(Authentication auth, Long projectId) {
        if (!isValidAuthentication(auth)) {
            return MilestonePermissionResponse.builder()
                    .canCreateMilestone(false)
                    .canViewMilestones(false)
                    .reason("Chưa đăng nhập")
                    .build();
        }

        if (projectId == null) {
            return MilestonePermissionResponse.builder()
                    .canCreateMilestone(false)
                    .canViewMilestones(false)
                    .reason("Project ID không được để trống")
                    .build();
        }

        ProjectContext context = loadProjectContext(auth, projectId);
        if (context == null) {
            return MilestonePermissionResponse.builder()
                    .canCreateMilestone(false)
                    .canViewMilestones(false)
                    .reason("Không thể load project context")
                    .build();
        }

        return MilestonePermissionResponse.builder()
                .userRole(context.userRole)
                .projectRole(context.projectRole)
                .canCreateMilestone(canCreateMilestone(context.userRole, context.projectRole, context.isProjectOwner))
                .canViewMilestones(canViewMilestones(context.userRole, context.projectRole, context.isProjectOwner))
                .reason(getMilestonePermissionReason(context.userRole, context.projectRole, context.isProjectOwner))
                .build();
    }

    private boolean canCreateMilestone(UserRole userRole, ProjectRole projectRole, boolean isProjectOwner) {
        return userRole == UserRole.PRODUCER && isProjectOwner;
    }

    private boolean canViewMilestones(UserRole userRole, ProjectRole projectRole, boolean isProjectOwner) {
        return userRole == UserRole.ADMIN || isProjectOwner || projectRole != null;
    }

    private String getMilestonePermissionReason(UserRole userRole, ProjectRole projectRole, boolean isProjectOwner) {
        if (userRole == null) {
            return "Người dùng không tồn tại";
        }
        
        if (userRole != UserRole.PRODUCER && userRole != UserRole.ADMIN) {
            return "Chỉ PRODUCER hoặc ADMIN mới có quyền thao tác với cột mốc";
        }
        
        if (!isProjectOwner && projectRole == null) {
            return "Bạn không phải là thành viên của dự án này";
        }
        
        if (userRole == UserRole.PRODUCER && !isProjectOwner) {
            return "Chỉ chủ dự án (PRODUCER với role OWNER) mới có quyền tạo cột mốc";
        }
        
        return null;
    }

    @Override
    public ContractPermissionResponse checkContractPermissions(Authentication auth, Long projectId) {
        if (!isValidAuthentication(auth)) {
            return ContractPermissionResponse.builder()
                    .canCreateContract(false)
                    .canViewContract(false)
                    .canInviteToSign(false)
                    .canDeclineContract(false)
                    .canEditContract(false)
                    .reason("Chưa đăng nhập")
                    .build();
        }

        if (projectId == null) {
            return ContractPermissionResponse.builder()
                    .canCreateContract(false)
                    .canViewContract(false)
                    .canInviteToSign(false)
                    .canDeclineContract(false)
                    .canEditContract(false)
                    .reason("Project ID không được để trống")
                    .build();
        }

        ProjectContext context = loadProjectContext(auth, projectId);
        if (context == null) {
            return ContractPermissionResponse.builder()
                    .canCreateContract(false)
                    .canViewContract(false)
                    .canInviteToSign(false)
                    .canDeclineContract(false)
                    .canEditContract(false)
                    .reason("Không thể load project context")
                    .build();
        }

        return ContractPermissionResponse.builder()
                .userRole(context.userRole)
                .projectRole(context.projectRole)
                .canCreateContract(canCreateContract(context.userRole, context.projectRole, context.isProjectOwner))
                .canViewContract(canViewContract(context.userRole, context.projectRole, context.isProjectOwner))
                .canInviteToSign(canInviteToSign(context.userRole, context.projectRole, context.isProjectOwner))
                .canDeclineContract(canDeclineContract(context.userRole, context.projectRole, context.isProjectOwner))
                .canEditContract(canEditContract(context.userRole, context.projectRole, context.isProjectOwner))
                .reason(getContractPermissionReason(context.userRole, context.projectRole, context.isProjectOwner))
                .build();
    }

    private boolean canCreateContract(UserRole userRole, ProjectRole projectRole, boolean isProjectOwner) {
        return userRole == UserRole.PRODUCER && isProjectOwner;
    }

    private boolean canViewContract(UserRole userRole, ProjectRole projectRole, boolean isProjectOwner) {
        return userRole == UserRole.ADMIN || isProjectOwner || projectRole == ProjectRole.CLIENT;
    }

    private boolean canInviteToSign(UserRole userRole, ProjectRole projectRole, boolean isProjectOwner) {
        return userRole == UserRole.ADMIN || isProjectOwner;
    }

    private boolean canDeclineContract(UserRole userRole, ProjectRole projectRole, boolean isProjectOwner) {
        return userRole == UserRole.ADMIN || projectRole == ProjectRole.CLIENT;
    }

    private boolean canEditContract(UserRole userRole, ProjectRole projectRole, boolean isProjectOwner) {
        return isProjectOwner;
    }

    private String getContractPermissionReason(UserRole userRole, ProjectRole projectRole, boolean isProjectOwner) {
        if (userRole == null) {
            return "Người dùng không tồn tại";
        }
        
        if (userRole != UserRole.PRODUCER && userRole != UserRole.ADMIN) {
            return "Chỉ PRODUCER hoặc ADMIN mới có quyền thao tác với hợp đồng";
        }
        
        if (!isProjectOwner && projectRole == null) {
            return "Bạn không phải là thành viên của dự án này";
        }
        
        if (userRole == UserRole.PRODUCER && !isProjectOwner) {
            return "Chỉ chủ dự án (PRODUCER) mới có quyền tạo hợp đồng";
        }
        
        return null;
    }

    private boolean canEditMilestone(UserRole userRole, ProjectRole projectRole, boolean isProjectOwner) {
        return userRole == UserRole.PRODUCER && isProjectOwner;
    }

    private boolean canDeleteMilestone(UserRole userRole, ProjectRole projectRole, boolean isProjectOwner) {
        return userRole == UserRole.PRODUCER && isProjectOwner;
    }

    private boolean canAddMembersToMilestone(UserRole userRole, ProjectRole projectRole, boolean isProjectOwner) {
        return userRole == UserRole.PRODUCER && isProjectOwner;
    }

    private boolean canRemoveMembersFromMilestone(UserRole userRole, ProjectRole projectRole, boolean isProjectOwner) {
        return userRole == UserRole.PRODUCER && isProjectOwner;
    }

    /**
     * Chấp nhận hoàn thành cột mốc:
     * - Chỉ CLIENT mới có quyền chấp nhận hoàn thành cột mốc
     * - Phải là project đã funded
     */
    private boolean canCompleteMilestone(ProjectRole projectRole, Project project) {
        // Chỉ CLIENT có quyền chấp nhận hoàn thành cột mốc
        if (projectRole != ProjectRole.CLIENT) {
            return false;
        }

        // Phải là project đã funded
        return project != null && Boolean.TRUE.equals(project.getIsFunded());
    }

    private boolean canCreatePayment(UserRole userRole, ProjectRole projectRole) {
        return projectRole == ProjectRole.CLIENT;
    }

    private boolean canViewPayment(UserRole userRole, boolean isProjectOwner, ProjectRole projectRole) {
        return userRole == UserRole.ADMIN || isProjectOwner || projectRole != null;
    }

    private boolean canCreateMoneySplit(UserRole userRole, ProjectRole projectRole, boolean isProjectOwner) {
        return isProjectOwner;
    }

    private boolean canUpdateMoneySplit(UserRole userRole, ProjectRole projectRole, boolean isProjectOwner) {
        return isProjectOwner;
    }

    private boolean canDeleteMoneySplit(UserRole userRole, ProjectRole projectRole, boolean isProjectOwner) {
        return isProjectOwner;
    }

    private boolean canApproveMoneySplit(UserRole userRole, ProjectRole projectRole, boolean isProjectMember) {
        return isProjectMember;
    }

    private boolean canRejectMoneySplit(UserRole userRole, ProjectRole projectRole, boolean isProjectMember) {
        return isProjectMember;
    }

    private boolean canViewMoneySplit(UserRole userRole, boolean isProjectOwner, ProjectRole projectRole) {
        // ADMIN và Owner luôn được xem
        if (userRole == UserRole.ADMIN || isProjectOwner) {
            return true;
        }
        // CLIENT không được xem phần phân chia tiền
        if (projectRole == ProjectRole.CLIENT) {
            return false;
        }
        // COLLABORATOR và OBSERVER được xem (nhưng sẽ chỉ thấy của mình nếu là milestone member)
        return projectRole == ProjectRole.COLLABORATOR || projectRole == ProjectRole.OBSERVER;
    }

    // Thêm các methods mới cho expense permissions
    private boolean canCreateExpense(UserRole userRole, ProjectRole projectRole, boolean isProjectOwner) {
        return isProjectOwner;
    }

    private boolean canUpdateExpense(UserRole userRole, ProjectRole projectRole, boolean isProjectOwner) {
        return isProjectOwner;
    }

    private boolean canDeleteExpense(UserRole userRole, ProjectRole projectRole, boolean isProjectOwner) {
        return isProjectOwner;
    }

    // Track permissions - cho phòng nội bộ, sync với TrackServiceImpl

    /**
     * Upload track:
     * - OWNER: luôn được phép
     * - COLLABORATOR: chỉ khi đã APPROVED Money Split
     */
    private boolean canUploadTrack(UserRole userRole, ProjectRole projectRole, boolean isProjectOwner, boolean hasApprovedMoneySplit) {
        if (isProjectOwner) {
            return true;
        }
        return projectRole == ProjectRole.COLLABORATOR && hasApprovedMoneySplit;
    }

    /**
     * View danh sách track:
     * - OWNER: luôn được phép
     * - COLLABORATOR: chỉ khi đã APPROVED Money Split
     */
    private boolean canViewTrack(UserRole userRole, ProjectRole projectRole, boolean isProjectOwner, boolean hasApprovedMoneySplit) {
        if (isProjectOwner) {
            return true;
        }
        return projectRole == ProjectRole.COLLABORATOR && hasApprovedMoneySplit;
    }

    /**
     * Update track:
     * - OWNER: luôn được phép
     * - COLLABORATOR: phải APPROVED Money Split.
     *   (check "chỉ được sửa track của chính mình" nằm ở TrackServiceImpl)
     */
    private boolean canUpdateTrack(UserRole userRole, ProjectRole projectRole, boolean isProjectOwner, boolean hasApprovedMoneySplit) {
        if (isProjectOwner) {
            return true;
        }
        return projectRole == ProjectRole.COLLABORATOR && hasApprovedMoneySplit;
    }

    /**
     * Delete track:
     * - OWNER: luôn được phép xóa track
     * - COLLABORATOR: chỉ khi đã APPROVED Money Split
     *   (check "chỉ được xóa track của chính mình" nằm ở TrackMilestoneServiceImpl)
     */
    private boolean canDeleteTrack(UserRole userRole, ProjectRole projectRole, boolean isProjectOwner, boolean hasApprovedMoneySplit) {
        if (isProjectOwner) {
            return true;
        }
        // COLLABORATOR có thể xóa track của chính mình (logic chi tiết check ở service layer)
        return projectRole == ProjectRole.COLLABORATOR && hasApprovedMoneySplit;
    }

    /**
     * Play track:
     * - SAME logic với view track, vì backend cũng dùng checkViewPermission
     */
    private boolean canPlayTrack(UserRole userRole, ProjectRole projectRole, boolean isProjectOwner, boolean hasApprovedMoneySplit) {
        if (isProjectOwner) {
            return true;
        }
        return projectRole == ProjectRole.COLLABORATOR && hasApprovedMoneySplit;
    }

    /**
     * Approve/Reject track status:
     * - Chỉ chủ dự án (OWNER) mới có quyền phê duyệt/từ chối trạng thái track
     */
    private boolean canApproveTrackStatus(boolean isProjectOwner) {
        return isProjectOwner;
    }

    // ==================== Client Delivery Permissions ====================

    /**
     * Gửi track cho client:
     * - Chỉ Owner được gửi
     */
    private boolean canSendTrackToClient(boolean isProjectOwner) {
        return isProjectOwner;
    }

    /**
     * Xem tracks trong Client Room:
     * - Owner: luôn được xem
     * - Admin: luôn được xem
     * - Client/Observer: chỉ khi project đã funded (type = COLLABORATIVE)
     */
    private boolean canViewClientTracks(UserRole userRole, boolean isProjectOwner, ProjectRole projectRole, Project project) {
        // Admin và Owner luôn xem được
        if (userRole == UserRole.ADMIN || isProjectOwner) {
            return true;
        }

        // Client và Observer chỉ xem được khi project đã funded
        if (projectRole == ProjectRole.CLIENT || projectRole == ProjectRole.OBSERVER) {
            return project != null && Boolean.TRUE.equals(project.getIsFunded());
        }

        return false;
    }

    /**
     * Chấp nhận sản phẩm (ACCEPTED):
     * - Owner: luôn được chấp nhận
     * - Admin: luôn được chấp nhận
     * - Client/Observer: chỉ khi project đã funded (giống như canViewClientTracks)
     */
    private boolean canAcceptDelivery(UserRole userRole, boolean isProjectOwner, ProjectRole projectRole, Project project) {
        // Admin và Owner luôn chấp nhận được
        if (userRole == UserRole.ADMIN || isProjectOwner) {
            return true;
        }

        // Client và Observer chỉ chấp nhận được khi project đã funded
        if (projectRole == ProjectRole.CLIENT || projectRole == ProjectRole.OBSERVER) {
            return project != null && Boolean.TRUE.equals(project.getIsFunded());
        }

        return false;
    }

    /**
     * Từ chối sản phẩm (REJECTED):
     * - Chỉ Client/Observer được từ chối
     * - Phải là project đã funded
     */
    private boolean canRejectDelivery(ProjectRole projectRole, Project project) {
        // Chỉ Client và Observer có quyền từ chối
        if (projectRole != ProjectRole.CLIENT && projectRole != ProjectRole.OBSERVER) {
            return false;
        }

        // Phải là project đã funded
        return project != null && Boolean.TRUE.equals(project.getIsFunded());
    }

    /**
     * Yêu cầu chỉnh sửa (REQUEST_EDIT):
     * - Chỉ Client/Observer được yêu cầu
     * - Phải là project đã funded
     */
    private boolean canRequestEditDelivery(ProjectRole projectRole, Project project) {
        // Chỉ Client và Observer có quyền yêu cầu chỉnh sửa
        if (projectRole != ProjectRole.CLIENT && projectRole != ProjectRole.OBSERVER) {
            return false;
        }

        // Phải là project đã funded
        return project != null && Boolean.TRUE.equals(project.getIsFunded());
    }

    /**
     * Xem số lượt gửi còn lại và số lượt chỉnh sửa:
     * - Owner: luôn xem được
     * - Admin: luôn xem được
     * - Client/Observer: xem được nếu project đã funded (để biết họ còn bao nhiêu quyền yêu cầu)
     */
    private boolean canViewProductCountRemaining(UserRole userRole, boolean isProjectOwner, ProjectRole projectRole, Project project) {
        // Admin và Owner luôn xem được
        if (userRole == UserRole.ADMIN || isProjectOwner) {
            return true;
        }
        
        // Client và Observer xem được nếu project đã funded
        if (projectRole == ProjectRole.CLIENT || projectRole == ProjectRole.OBSERVER) {
            return project != null && Boolean.TRUE.equals(project.getIsFunded());
        }
        
        return false;
    }

    /**
     * Hủy delivery (rollback):
     * - Chỉ Owner được hủy
     */
    private boolean canCancelDelivery(boolean isProjectOwner) {
        return isProjectOwner;
    }

    // ==================== Client Room Comment Permissions ====================

    /**
     * Tạo comment trong Client Room:
     * - Owner: luôn được tạo
     * - Admin: luôn được tạo
     * - Client/Observer: chỉ khi project đã funded (giống như canViewClientTracks)
     */
    private boolean canCreateClientRoomComment(UserRole userRole, boolean isProjectOwner, ProjectRole projectRole, Project project) {
        // Admin và Owner luôn tạo được
        if (userRole == UserRole.ADMIN || isProjectOwner) {
            return true;
        }

        // Client và Observer chỉ tạo được khi project đã funded
        if (projectRole == ProjectRole.CLIENT || projectRole == ProjectRole.OBSERVER) {
            return project != null && Boolean.TRUE.equals(project.getIsFunded());
        }

        return false;
    }

    /**
     * Xem comment trong Client Room:
     * - Owner: luôn được xem
     * - Admin: luôn được xem
     * - Client/Observer: chỉ khi project đã funded (giống như canViewClientTracks)
     */
    private boolean canViewClientRoomComments(UserRole userRole, boolean isProjectOwner, ProjectRole projectRole, Project project) {
        // Admin và Owner luôn xem được
        if (userRole == UserRole.ADMIN || isProjectOwner) {
            return true;
        }

        // Client và Observer chỉ xem được khi project đã funded
        if (projectRole == ProjectRole.CLIENT || projectRole == ProjectRole.OBSERVER) {
            return project != null && Boolean.TRUE.equals(project.getIsFunded());
        }

        return false;
    }

    /**
     * Sửa comment trong Client Room:
     * - Owner: luôn được sửa (nhưng chỉ sửa được comment của mình trong business logic)
     * - Admin: luôn được sửa
     * - Client/Observer: chỉ khi project đã funded (nhưng chỉ sửa được comment của mình trong business logic)
     * 
     * Note: Business logic check "chỉ được sửa comment của mình" nằm ở TrackCommentServiceImpl
     */
    private boolean canUpdateClientRoomComment(UserRole userRole, boolean isProjectOwner, ProjectRole projectRole, Project project) {
        // Admin và Owner luôn sửa được
        if (userRole == UserRole.ADMIN || isProjectOwner) {
            return true;
        }

        // Client và Observer chỉ sửa được khi project đã funded
        if (projectRole == ProjectRole.CLIENT || projectRole == ProjectRole.OBSERVER) {
            return project != null && Boolean.TRUE.equals(project.getIsFunded());
        }

        return false;
    }

    /**
     * Xóa comment trong Client Room:
     * - Owner: luôn được xóa (có thể xóa comment của bất kỳ ai)
     * - Admin: luôn được xóa
     * - Client/Observer: chỉ khi project đã funded (nhưng chỉ xóa được comment của mình hoặc track owner trong business logic)
     * 
     * Note: Business logic check "chỉ được xóa comment của mình hoặc track owner" nằm ở TrackCommentServiceImpl
     */
    private boolean canDeleteClientRoomComment(UserRole userRole, boolean isProjectOwner, ProjectRole projectRole, Project project) {
        // Admin và Owner luôn xóa được
        if (userRole == UserRole.ADMIN || isProjectOwner) {
            return true;
        }

        // Client và Observer chỉ xóa được khi project đã funded
        if (projectRole == ProjectRole.CLIENT || projectRole == ProjectRole.OBSERVER) {
            return project != null && Boolean.TRUE.equals(project.getIsFunded());
        }

        return false;
    }

    /**
     * Cập nhật status comment trong Client Room:
     * - Chỉ Owner được cập nhật status (giống như Internal Room)
     */
    private boolean canUpdateClientRoomCommentStatus(boolean isProjectOwner) {
        return isProjectOwner;
    }
}
