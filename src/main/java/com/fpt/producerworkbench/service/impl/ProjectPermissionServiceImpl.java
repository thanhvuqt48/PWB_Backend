package com.fpt.producerworkbench.service.impl;

import com.fpt.producerworkbench.common.ProjectRole;
import com.fpt.producerworkbench.common.UserRole;
import com.fpt.producerworkbench.dto.response.ContractPermissionResponse;
import com.fpt.producerworkbench.dto.response.MilestonePermissionResponse;
import com.fpt.producerworkbench.dto.response.ProjectPermissionResponse;
import com.fpt.producerworkbench.entity.Project;
import com.fpt.producerworkbench.entity.ProjectMember;
import com.fpt.producerworkbench.entity.User;
import com.fpt.producerworkbench.exception.AppException;
import com.fpt.producerworkbench.exception.ErrorCode;
import com.fpt.producerworkbench.repository.ProjectMemberRepository;
import com.fpt.producerworkbench.repository.ProjectRepository;
import com.fpt.producerworkbench.repository.UserRepository;
import com.fpt.producerworkbench.service.ProjectPermissionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProjectPermissionServiceImpl implements ProjectPermissionService {

    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;

    /**
     * Context class chứa thông tin chung về user và project để tránh query database nhiều lần
     */
    private static class ProjectContext {
        final UserRole userRole;
        final boolean isProjectOwner;
        final ProjectRole projectRole;
        final boolean isProjectMember;
        final boolean isAnonymousMember;

        ProjectContext(UserRole userRole, boolean isProjectOwner, ProjectRole role, boolean isProjectMember, boolean isAnonymousMember) {
            this.userRole = userRole;
            this.isProjectOwner = isProjectOwner;
            this.projectRole = role;
            this.isProjectMember = isProjectMember;
            this.isAnonymousMember = isAnonymousMember;
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

        return new ProjectContext(userRole, isProjectOwner, projectRole, isProjectMember, isAnonymousMember);
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
                        .canViewProject(false)
                        .canEditProject(false)
                        .canDeleteProject(false)
                        .canViewMembers(false)
                        .canManageInvitations(false)
                        .canAcceptInvitation(false)
                        .canDeclineInvitation(false)
                        .canViewMyInvitations(false)
                        .build())
                .milestone(ProjectPermissionResponse.MilestonePermissions.builder()
                        .canCreateMilestone(false)
                        .canViewMilestones(false)
                        .canEditMilestone(false)
                        .canDeleteMilestone(false)
                        .canAddMembersToMilestone(false)
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

        return ProjectPermissionResponse.builder()
                // Role information
                .role(ProjectPermissionResponse.RoleInfo.builder()
                        .userRole(context.userRole)
                        .projectRole(context.projectRole)
                        .anonymous(context.isAnonymousMember)
                        .build())
                
                .project(ProjectPermissionResponse.ProjectPermissions.builder()
                        .canCreateProject(canCreateProject(context.userRole))
                        .canInviteMembers(canInviteMembers(context.userRole, context.isProjectOwner))
                        .canViewProject(canViewProject(context.userRole, context.isProjectOwner, context.projectRole))
                        .canEditProject(canEditProject(context.userRole, context.isProjectOwner))
                        .canDeleteProject(canDeleteProject(context.userRole, context.isProjectOwner))
                        .canViewMembers(canViewMembers(context.userRole, context.isProjectOwner, context.projectRole))
                        .canManageInvitations(canManageInvitations(context.userRole, context.isProjectOwner))
                        .canAcceptInvitation(true)
                        .canDeclineInvitation(true)
                        .canViewMyInvitations(true) 
                        .build())
                
                // Milestone permissions
                .milestone(ProjectPermissionResponse.MilestonePermissions.builder()
                        .canCreateMilestone(canCreateMilestone(context.userRole, context.projectRole, context.isProjectOwner))
                        .canViewMilestones(canViewMilestones(context.userRole, context.projectRole, context.isProjectOwner))
                        .canEditMilestone(canEditMilestone(context.userRole, context.projectRole, context.isProjectOwner))
                        .canDeleteMilestone(canDeleteMilestone(context.userRole, context.projectRole, context.isProjectOwner))
                        .canAddMembersToMilestone(canAddMembersToMilestone(context.userRole, context.projectRole, context.isProjectOwner))
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
                
                .reason(getPermissionReason(context.userRole, context.projectRole, context.isProjectOwner, context.isProjectMember))
                .build();
    }

    private boolean canCreateProject(UserRole userRole) {
        return userRole == UserRole.PRODUCER || userRole == UserRole.ADMIN;
    }

    private boolean canInviteMembers(UserRole userRole, boolean isProjectOwner) {
        return userRole == UserRole.ADMIN || isProjectOwner;
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
        return userRole == UserRole.ADMIN || isProjectOwner || projectRole != null;
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
}
