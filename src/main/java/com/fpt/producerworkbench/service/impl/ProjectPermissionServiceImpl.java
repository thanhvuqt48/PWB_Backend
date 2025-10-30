package com.fpt.producerworkbench.service.impl;

import com.fpt.producerworkbench.common.ProjectRole;
import com.fpt.producerworkbench.common.UserRole;
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

    @Override
    public ProjectPermissionResponse checkProjectPermissions(Authentication auth, Long projectId) {
        if (auth == null || auth.getName() == null || auth.getName().isBlank()) {
            return ProjectPermissionResponse.builder()
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
                    .reason("Chưa đăng nhập")
                    .build();
        }

        User user = userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        UserRole userRole = user.getRole();

        if (projectId == null) {
            return ProjectPermissionResponse.builder()
                    .userRole(userRole)
                    .canCreateProject(canCreateProject(userRole))
                    .canViewMyInvitations(true)
                    .reason(getCreateProjectReason(userRole))
                    .build();
        }

        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new AppException(ErrorCode.PROJECT_NOT_FOUND));

        boolean isProjectOwner = project.getCreator() != null &&
                user.getId().equals(project.getCreator().getId());

        Optional<ProjectMember> memberOpt = projectMemberRepository
                .findByProjectIdAndUserEmail(projectId, user.getEmail());
        
        ProjectRole projectRole = null;
        boolean isProjectMember = false;
        if (isProjectOwner) {
            projectRole = ProjectRole.OWNER;
            isProjectMember = true;
        } else if (memberOpt.isPresent()) {
            projectRole = memberOpt.get().getProjectRole();
            isProjectMember = true;
        }

        return ProjectPermissionResponse.builder()
                .userRole(userRole)
                .projectRole(projectRole)
                .isProjectOwner(isProjectOwner)
                .isProjectMember(isProjectMember)
                .canCreateProject(canCreateProject(userRole))
                .canInviteMembers(canInviteMembers(userRole, isProjectOwner))
                .canViewProject(canViewProject(userRole, isProjectOwner, projectRole))
                .canEditProject(canEditProject(userRole, isProjectOwner))
                .canDeleteProject(canDeleteProject(userRole, isProjectOwner))
                .canViewMembers(canViewMembers(userRole, isProjectOwner, projectRole))
                .canManageInvitations(canManageInvitations(userRole, isProjectOwner))
                .canAcceptInvitation(true)
                .canDeclineInvitation(true)
                .canViewMyInvitations(true)
                .reason(getPermissionReason(userRole, projectRole, isProjectOwner, isProjectMember))
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

    private String getCreateProjectReason(UserRole userRole) {
        if (userRole == null) {
            return "Người dùng không tồn tại";
        }
        
        if (userRole != UserRole.PRODUCER && userRole != UserRole.ADMIN) {
            return "Chỉ PRODUCER hoặc ADMIN mới có quyền tạo dự án";
        }
        
        return null;
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
}
