package com.fpt.producerworkbench.service;

import com.fpt.producerworkbench.dto.request.ProjectReviewRequest;
import com.fpt.producerworkbench.dto.response.ProjectReviewResponse;
import org.springframework.security.core.Authentication;

import java.util.List;

public interface ProjectReviewService {

    ProjectReviewResponse createReview(Long projectId, ProjectReviewRequest request, Authentication auth);

    List<ProjectReviewResponse> getProducerPublicPortfolio(Long producerId);

    ProjectReviewResponse getProjectReview(Long projectId, Authentication auth);

    ProjectReviewResponse updateReview(Long projectId, ProjectReviewRequest request, Authentication auth);

    void deleteReview(Long projectId, Authentication auth);
}