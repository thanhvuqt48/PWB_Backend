package com.fpt.producerworkbench.controller;

import com.fpt.producerworkbench.dto.request.ProjectReviewRequest;
import com.fpt.producerworkbench.dto.response.ApiResponse;
import com.fpt.producerworkbench.dto.response.ProjectReviewResponse;
import com.fpt.producerworkbench.service.ProjectReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controller quản lý các thao tác liên quan đến đánh giá dự án (Project Review).
 * Bao gồm: tạo đánh giá cho dự án và xem portfolio công khai của producer.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ProjectReviewController {

    private final ProjectReviewService projectReviewService;

    /**
     * Tạo đánh giá cho một dự án đã hoàn thành.
     * Chỉ client của dự án mới có thể đánh giá.
     * Mỗi dự án chỉ được đánh giá một lần.
     *
     * @param projectId ID của dự án cần đánh giá
     * @param request Thông tin đánh giá (rating, comment, allowPublicPortfolio)
     * @param auth Authentication object
     * @return ProjectReviewResponse chứa thông tin đánh giá vừa tạo
     */
    @PostMapping("/projects/{projectId}/reviews")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<ProjectReviewResponse>> createReview(
            @PathVariable Long projectId,
            @Valid @RequestBody ProjectReviewRequest request,
            Authentication auth) {

        ProjectReviewResponse review = projectReviewService.createReview(projectId, request, auth);

        ApiResponse<ProjectReviewResponse> response = ApiResponse.<ProjectReviewResponse>builder()
                .message("Đánh giá đã được gửi thành công")
                .result(review)
                .build();

        return ResponseEntity.ok(response);
    }

    /**
     * Lấy danh sách đánh giá công khai (portfolio) của một producer.
     * Chỉ hiển thị các đánh giá mà client cho phép công khai (allowPublicPortfolio = true).
     * Endpoint này có thể truy cập công khai, không cần authentication.
     *
     * @param producerId ID của producer
     * @return Danh sách các đánh giá công khai
     */
    @GetMapping("/producers/{producerId}/portfolio")
    public ResponseEntity<ApiResponse<List<ProjectReviewResponse>>> getProducerPublicPortfolio(
            @PathVariable Long producerId) {

        List<ProjectReviewResponse> reviews = projectReviewService.getProducerPublicPortfolio(producerId);

        ApiResponse<List<ProjectReviewResponse>> response = ApiResponse.<List<ProjectReviewResponse>>builder()
                .message("Lấy portfolio công khai thành công")
                .result(reviews)
                .build();

        return ResponseEntity.ok(response);
    }

    /**
     * Lấy đánh giá của một dự án cụ thể.
     * Client và Owner của project có thể xem.
     *
     * @param projectId ID của dự án
     * @param auth Authentication object
     * @return Thông tin đánh giá
     */
    @GetMapping("/projects/{projectId}/reviews")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<ProjectReviewResponse>> getProjectReview(
            @PathVariable Long projectId,
            Authentication auth) {

        ProjectReviewResponse review = projectReviewService.getProjectReview(projectId, auth);

        ApiResponse<ProjectReviewResponse> response = ApiResponse.<ProjectReviewResponse>builder()
                .message("Lấy đánh giá thành công")
                .result(review)
                .build();

        return ResponseEntity.ok(response);
    }

    /**
     * Cập nhật đánh giá của dự án.
     * Chỉ client đã tạo review mới có thể cập nhật.
     *
     * @param projectId ID của dự án
     * @param request Thông tin cập nhật
     * @param auth Authentication object
     * @return Thông tin đánh giá sau khi cập nhật
     */
    @PutMapping("/projects/{projectId}/reviews")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<ProjectReviewResponse>> updateReview(
            @PathVariable Long projectId,
            @Valid @RequestBody ProjectReviewRequest request,
            Authentication auth) {

        ProjectReviewResponse review = projectReviewService.updateReview(projectId, request, auth);

        ApiResponse<ProjectReviewResponse> response = ApiResponse.<ProjectReviewResponse>builder()
                .message("Cập nhật đánh giá thành công")
                .result(review)
                .build();

        return ResponseEntity.ok(response);
    }

    /**
     * Xóa đánh giá của dự án.
     * Chỉ client đã tạo review mới có thể xóa.
     *
     * @param projectId ID của dự án
     * @param auth Authentication object
     * @return Thông báo thành công
     */
    @DeleteMapping("/projects/{projectId}/reviews")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> deleteReview(
            @PathVariable Long projectId,
            Authentication auth) {

        projectReviewService.deleteReview(projectId, auth);

        ApiResponse<Void> response = ApiResponse.<Void>builder()
                .message("Xóa đánh giá thành công")
                .build();

        return ResponseEntity.ok(response);
    }
}

