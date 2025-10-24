package com.fpt.producerworkbench.service.impl;

import com.fpt.producerworkbench.common.ProjectRole;
import com.fpt.producerworkbench.common.UserRole;
import com.fpt.producerworkbench.dto.response.ContractPermissionResponse;
import com.fpt.producerworkbench.entity.Project;
import com.fpt.producerworkbench.entity.ProjectMember;
import com.fpt.producerworkbench.entity.User;
import com.fpt.producerworkbench.exception.AppException;
import com.fpt.producerworkbench.exception.ErrorCode;
import com.fpt.producerworkbench.repository.ProjectMemberRepository;
import com.fpt.producerworkbench.repository.ProjectRepository;
import com.fpt.producerworkbench.repository.UserRepository;
import com.fpt.producerworkbench.service.ContractPermissionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ContractPermissionServiceImpl implements ContractPermissionService {

    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;

    @Override
    public ContractPermissionResponse checkContractPermissions(Authentication auth, Long projectId) {
        if (auth == null || auth.getName() == null || auth.getName().isBlank()) {
            return ContractPermissionResponse.builder()
                    .canCreateContract(false)
                    .canViewContract(false)
                    .canInviteToSign(false)
                    .canDeclineContract(false)
                    .canEditContract(false)
                    .reason("Chưa đăng nhập")
                    .build();
        }

        User user = userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new AppException(ErrorCode.PROJECT_NOT_FOUND));

        boolean isProjectOwner = project.getCreator() != null && 
                user.getId().equals(project.getCreator().getId());

        Optional<ProjectMember> memberOpt = projectMemberRepository
                .findByProjectIdAndUserEmail(projectId, user.getEmail());
        
        ProjectRole projectRole = null;
        if (isProjectOwner) {
            projectRole = ProjectRole.OWNER;
        } else if (memberOpt.isPresent()) {
            projectRole = memberOpt.get().getProjectRole();
        }

        UserRole userRole = user.getRole();

        return ContractPermissionResponse.builder()
                .userRole(userRole)
                .projectRole(projectRole)
                .canCreateContract(canCreateContract(userRole, projectRole, isProjectOwner))
                .canViewContract(canViewContract(userRole, projectRole, isProjectOwner))
                .canInviteToSign(canInviteToSign(userRole, projectRole, isProjectOwner))
                .canDeclineContract(canDeclineContract(userRole, projectRole, isProjectOwner))
                .canEditContract(canEditContract(userRole, projectRole, isProjectOwner))
                .reason(getPermissionReason(userRole, projectRole, isProjectOwner))
                .build();
    }

    private boolean canCreateContract(UserRole userRole, ProjectRole projectRole, boolean isProjectOwner) {
        // Chỉ PRODUCER với vai trò OWNER trong project mới được tạo hợp đồng
        return userRole == UserRole.PRODUCER && isProjectOwner;
    }

    private boolean canViewContract(UserRole userRole, ProjectRole projectRole, boolean isProjectOwner) {
        // ADMIN, OWNER, hoặc CLIENT có thể xem hợp đồng
        return userRole == UserRole.ADMIN || isProjectOwner || projectRole == ProjectRole.CLIENT;
    }

    private boolean canInviteToSign(UserRole userRole, ProjectRole projectRole, boolean isProjectOwner) {
        // ADMIN hoặc OWNER có thể mời ký
        return userRole == UserRole.ADMIN || isProjectOwner;
    }

    private boolean canDeclineContract(UserRole userRole, ProjectRole projectRole, boolean isProjectOwner) {
        // ADMIN hoặc CLIENT có thể từ chối
        return userRole == UserRole.ADMIN || projectRole == ProjectRole.CLIENT;
    }

    private boolean canEditContract(UserRole userRole, ProjectRole projectRole, boolean isProjectOwner) {
        // Chỉ OWNER có thể chỉnh sửa hợp đồng (nếu chưa hoàn tất)
        return isProjectOwner;
    }

    private String getPermissionReason(UserRole userRole, ProjectRole projectRole, boolean isProjectOwner) {
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
        
        return null; // Có quyền
    }
}
