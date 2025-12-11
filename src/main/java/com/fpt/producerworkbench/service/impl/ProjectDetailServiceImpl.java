package com.fpt.producerworkbench.service.impl;

import com.fpt.producerworkbench.dto.response.ProjectDetailResponse;
import com.fpt.producerworkbench.entity.Project;
import com.fpt.producerworkbench.entity.ProjectMember;
import com.fpt.producerworkbench.entity.User;
import com.fpt.producerworkbench.exception.AppException;
import com.fpt.producerworkbench.exception.ErrorCode;
import com.fpt.producerworkbench.repository.ProjectMemberRepository;
import com.fpt.producerworkbench.repository.ProjectRepository;
import com.fpt.producerworkbench.repository.ContractRepository;
import com.fpt.producerworkbench.repository.UserRepository;
import com.fpt.producerworkbench.service.ProjectDetailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProjectDetailServiceImpl implements ProjectDetailService {

    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final UserRepository userRepository;
    private final ContractRepository contractRepository;

    @Override
    public ProjectDetailResponse getProjectDetail(Authentication auth, Long projectId) {
        if (auth == null || auth.getName() == null || auth.getName().isBlank()) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        User currentUser = userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new AppException(ErrorCode.PROJECT_NOT_FOUND));

        boolean isProjectOwner = project.getCreator() != null &&
                currentUser.getId().equals(project.getCreator().getId());
        
        Optional<ProjectMember> memberOpt = projectMemberRepository
                .findByProjectIdAndUserEmail(projectId, currentUser.getEmail());
        
        if (!isProjectOwner && memberOpt.isEmpty()) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }

        var contractOpt = contractRepository.findByProjectId(projectId);

        return ProjectDetailResponse.builder()
                .id(project.getId())
                .title(project.getTitle())
                .description(project.getDescription())
                .status(project.getStatus())
                .type(project.getType())
                .creatorName(project.getCreator() != null ? project.getCreator().getFullName() : null)
                .creatorAvatarUrl(project.getCreator() != null ? project.getCreator().getAvatarUrl() : null)
                .createdAt(project.getCreatedAt())
                .startDate(project.getStartDate())
                .completedAt(project.getCompletedAt())
                .totalAmount(contractOpt.map(c -> c.getTotalAmount()).orElse(null))
                .paymentType(contractOpt.map(c -> c.getPaymentType()).orElse(null))
                .productCount(contractOpt.map(c -> c.getProductCount()).orElse(null))
                .fpEditAmount(contractOpt.map(c -> c.getFpEditAmount()).orElse(null))
                .build();
    }
}
