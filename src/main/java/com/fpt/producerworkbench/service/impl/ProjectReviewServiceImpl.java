package com.fpt.producerworkbench.service.impl;

import com.fpt.producerworkbench.common.ProjectStatus;
import com.fpt.producerworkbench.dto.request.ProjectReviewRequest;
import com.fpt.producerworkbench.dto.response.ProjectReviewResponse;
import com.fpt.producerworkbench.entity.Project;
import com.fpt.producerworkbench.entity.ProjectReview;
import com.fpt.producerworkbench.entity.User;
import com.fpt.producerworkbench.exception.AppException;
import com.fpt.producerworkbench.exception.ErrorCode;
import com.fpt.producerworkbench.repository.ProjectRepository;
import com.fpt.producerworkbench.repository.ProjectReviewRepository;
import com.fpt.producerworkbench.repository.UserRepository;
import com.fpt.producerworkbench.service.ProjectReviewService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.ZoneId;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProjectReviewServiceImpl implements ProjectReviewService {

    private final ProjectRepository projectRepository;
    private final ProjectReviewRepository reviewRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public ProjectReviewResponse createReview(Long projectId, ProjectReviewRequest request, Authentication auth) {
        User currentUser = userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new AppException(ErrorCode.PROJECT_NOT_FOUND));

        if (project.getClient() == null || !currentUser.getId().equals(project.getClient().getId())) {
            throw new AppException(ErrorCode.ACCESS_DENIED, "Bạn không có quyền đánh giá dự án này");
        }

        if (project.getStatus() != ProjectStatus.COMPLETED) {
            throw new AppException(ErrorCode.INVALID_ACTION, "Dự án phải hoàn thành mới được phép đánh giá");
        }

        if (reviewRepository.existsByProjectId(projectId)) {
            throw new AppException(ErrorCode.RESOURCE_EXISTED, "Bạn đã gửi đánh giá cho dự án này rồi");
        }

        boolean isAllowedPublic = Boolean.TRUE.equals(request.getAllowPublicPortfolio());

        ProjectReview review = ProjectReview.builder()
                .project(project)
                .reviewer(currentUser)
                .targetUser(project.getCreator())
                .rating(request.getRating())
                .comment(request.getComment())
                .allowPublicPortfolio(isAllowedPublic)
                .build();

        reviewRepository.save(review);
        log.info("Client {} reviewed Project {}. Public Allowed: {}", currentUser.getEmail(), projectId, isAllowedPublic);

        return mapToResponse(review);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProjectReviewResponse> getProducerPublicPortfolio(Long producerId) {
        List<ProjectReview> reviews = reviewRepository.findPublicPortfolioByProducerId(producerId);

        return reviews.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    private ProjectReviewResponse mapToResponse(ProjectReview review) {
        java.time.LocalDateTime createdDate = null;
        if (review.getCreatedAt() != null) {
            createdDate = review.getCreatedAt().toInstant()
                    .atZone(ZoneId.systemDefault())
                    .toLocalDateTime();
        }

        return ProjectReviewResponse.builder()
                .id(review.getId())
                .projectId(review.getProject().getId())
                .projectTitle(review.getProject().getTitle())
                .producerName(review.getTargetUser().getFullName())
                .rating(review.getRating())
                .comment(review.getComment())
                .allowPublicPortfolio(review.isAllowPublicPortfolio())
                .createdAt(createdDate)
                .build();
    }
}