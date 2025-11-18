package com.fpt.producerworkbench.controller;

import com.fpt.producerworkbench.dto.request.TrackCommentCreateRequest;
import com.fpt.producerworkbench.dto.request.TrackCommentStatusUpdateRequest;
import com.fpt.producerworkbench.dto.request.TrackCommentUpdateRequest;
import com.fpt.producerworkbench.dto.response.ApiResponse;
import com.fpt.producerworkbench.dto.response.TrackCommentResponse;
import com.fpt.producerworkbench.dto.response.TrackCommentStatisticsResponse;
import com.fpt.producerworkbench.exception.AppException;
import com.fpt.producerworkbench.exception.ErrorCode;
import com.fpt.producerworkbench.service.TrackCommentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class TrackCommentController {

    private final TrackCommentService trackCommentService;

    /**
     * Tạo comment mới trên track
     * Tự động gửi email thông báo cho track owner
     */
    @PostMapping("/tracks/{trackId}/comments")
    public ApiResponse<TrackCommentResponse> createComment(
            @PathVariable Long trackId,
            @Valid @RequestBody TrackCommentCreateRequest request,
            Authentication authentication) {

        if (trackId == null || trackId <= 0) {
            throw new AppException(ErrorCode.INVALID_PARAMETER_FORMAT, "Track ID không hợp lệ");
        }

        TrackCommentResponse response = trackCommentService.createComment(authentication, trackId, request);

        return ApiResponse.<TrackCommentResponse>builder()
                .code(HttpStatus.CREATED.value())
                .message("Đã tạo comment thành công")
                .result(response)
                .build();
    }

    /**
     * Lấy danh sách comment gốc (không có parent) của track với pagination
     * Sắp xếp theo timestamp tăng dần
     */
    @GetMapping("/tracks/{trackId}/comments")
    public ApiResponse<Page<TrackCommentResponse>> getRootCommentsByTrack(
            @PathVariable Long trackId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {

        if (trackId == null || trackId <= 0) {
            throw new AppException(ErrorCode.INVALID_PARAMETER_FORMAT, "Track ID không hợp lệ");
        }

        if (size > 100) {
            size = 100; // Giới hạn tối đa 100 items per page
        }

        Pageable pageable = PageRequest.of(page, size);
        Page<TrackCommentResponse> response = trackCommentService.getRootCommentsByTrack(
                authentication, trackId, pageable);

        return ApiResponse.<Page<TrackCommentResponse>>builder()
                .code(HttpStatus.OK.value())
                .message("Lấy danh sách comment thành công")
                .result(response)
                .build();
    }

    /**
     * Lấy danh sách reply của một comment
     */
    @GetMapping("/comments/{commentId}/replies")
    public ApiResponse<List<TrackCommentResponse>> getRepliesByComment(
            @PathVariable Long commentId,
            Authentication authentication) {

        if (commentId == null || commentId <= 0) {
            throw new AppException(ErrorCode.INVALID_PARAMETER_FORMAT, "Comment ID không hợp lệ");
        }

        List<TrackCommentResponse> response = trackCommentService.getRepliesByComment(
                authentication, commentId);

        return ApiResponse.<List<TrackCommentResponse>>builder()
                .code(HttpStatus.OK.value())
                .message("Lấy danh sách reply thành công")
                .result(response)
                .build();
    }

    /**
     * Lấy thông tin chi tiết một comment (bao gồm replies)
     */
    @GetMapping("/comments/{commentId}")
    public ApiResponse<TrackCommentResponse> getCommentById(
            @PathVariable Long commentId,
            Authentication authentication) {

        if (commentId == null || commentId <= 0) {
            throw new AppException(ErrorCode.INVALID_PARAMETER_FORMAT, "Comment ID không hợp lệ");
        }

        TrackCommentResponse response = trackCommentService.getCommentById(authentication, commentId);

        return ApiResponse.<TrackCommentResponse>builder()
                .code(HttpStatus.OK.value())
                .message("Lấy thông tin comment thành công")
                .result(response)
                .build();
    }

    /**
     * Cập nhật nội dung comment
     * Chỉ user tạo comment mới có quyền
     */
    @PutMapping("/comments/{commentId}")
    public ApiResponse<TrackCommentResponse> updateComment(
            @PathVariable Long commentId,
            @Valid @RequestBody TrackCommentUpdateRequest request,
            Authentication authentication) {

        if (commentId == null || commentId <= 0) {
            throw new AppException(ErrorCode.INVALID_PARAMETER_FORMAT, "Comment ID không hợp lệ");
        }

        TrackCommentResponse response = trackCommentService.updateComment(
                authentication, commentId, request);

        return ApiResponse.<TrackCommentResponse>builder()
                .code(HttpStatus.OK.value())
                .message("Đã cập nhật comment thành công")
                .result(response)
                .build();
    }

    /**
     * Xóa comment (soft delete)
     * Chỉ user tạo comment hoặc track owner mới có quyền
     */
    @DeleteMapping("/comments/{commentId}")
    public ApiResponse<Void> deleteComment(
            @PathVariable Long commentId,
            Authentication authentication) {

        if (commentId == null || commentId <= 0) {
            throw new AppException(ErrorCode.INVALID_PARAMETER_FORMAT, "Comment ID không hợp lệ");
        }

        trackCommentService.deleteComment(authentication, commentId);

        return ApiResponse.<Void>builder()
                .code(HttpStatus.OK.value())
                .message("Đã xóa comment thành công")
                .build();
    }

    /**
     * Cập nhật trạng thái của comment
     * Chỉ track owner mới có quyền
     * Tự động gửi email thông báo cho comment owner
     */
    @PutMapping("/comments/{commentId}/status")
    public ApiResponse<TrackCommentResponse> updateCommentStatus(
            @PathVariable Long commentId,
            @Valid @RequestBody TrackCommentStatusUpdateRequest request,
            Authentication authentication) {

        if (commentId == null || commentId <= 0) {
            throw new AppException(ErrorCode.INVALID_PARAMETER_FORMAT, "Comment ID không hợp lệ");
        }

        TrackCommentResponse response = trackCommentService.updateCommentStatus(
                authentication, commentId, request);

        return ApiResponse.<TrackCommentResponse>builder()
                .code(HttpStatus.OK.value())
                .message("Đã cập nhật trạng thái comment thành công")
                .result(response)
                .build();
    }

    /**
     * Lấy thống kê comment của track theo status
     */
    @GetMapping("/tracks/{trackId}/comments/statistics")
    public ApiResponse<TrackCommentStatisticsResponse> getCommentStatistics(
            @PathVariable Long trackId,
            Authentication authentication) {

        if (trackId == null || trackId <= 0) {
            throw new AppException(ErrorCode.INVALID_PARAMETER_FORMAT, "Track ID không hợp lệ");
        }

        TrackCommentStatisticsResponse response = trackCommentService.getCommentStatistics(
                authentication, trackId);

        return ApiResponse.<TrackCommentStatisticsResponse>builder()
                .code(HttpStatus.OK.value())
                .message("Lấy thống kê comment thành công")
                .result(response)
                .build();
    }

    /**
     * Lấy danh sách comment tại một timestamp cụ thể
     * Hữu ích để hiển thị comments tại điểm thời gian cụ thể trong track
     */
    @GetMapping("/tracks/{trackId}/comments/by-timestamp")
    public ApiResponse<List<TrackCommentResponse>> getCommentsByTimestamp(
            @PathVariable Long trackId,
            @RequestParam Integer timestamp,
            Authentication authentication) {

        if (trackId == null || trackId <= 0) {
            throw new AppException(ErrorCode.INVALID_PARAMETER_FORMAT, "Track ID không hợp lệ");
        }

        if (timestamp == null || timestamp < 0) {
            throw new AppException(ErrorCode.INVALID_PARAMETER_FORMAT, "Timestamp không hợp lệ");
        }

        List<TrackCommentResponse> response = trackCommentService.getCommentsByTimestamp(
                authentication, trackId, timestamp);

        return ApiResponse.<List<TrackCommentResponse>>builder()
                .code(HttpStatus.OK.value())
                .message("Lấy comment theo timestamp thành công")
                .result(response)
                .build();
    }

    // ==================== Client Room Comments ====================

    /**
     * Tạo comment mới trong Client Room
     * Permission: Owner, Admin, Client, Observer (nếu project funded)
     */
    @PostMapping("/client-deliveries/{deliveryId}/comments")
    public ApiResponse<TrackCommentResponse> createClientRoomComment(
            @PathVariable Long deliveryId,
            @Valid @RequestBody TrackCommentCreateRequest request,
            Authentication authentication) {

        if (deliveryId == null || deliveryId <= 0) {
            throw new AppException(ErrorCode.INVALID_PARAMETER_FORMAT, "Delivery ID không hợp lệ");
        }

        TrackCommentResponse response = trackCommentService.createClientRoomComment(
                authentication, deliveryId, request);

        return ApiResponse.<TrackCommentResponse>builder()
                .code(HttpStatus.CREATED.value())
                .message("Đã tạo comment trong Client Room thành công")
                .result(response)
                .build();
    }

    /**
     * Lấy danh sách comment gốc trong Client Room với pagination
     * Permission: Owner, Admin, Client, Observer (nếu project funded)
     */
    @GetMapping("/client-deliveries/{deliveryId}/comments")
    public ApiResponse<Page<TrackCommentResponse>> getClientRoomComments(
            @PathVariable Long deliveryId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {

        if (deliveryId == null || deliveryId <= 0) {
            throw new AppException(ErrorCode.INVALID_PARAMETER_FORMAT, "Delivery ID không hợp lệ");
        }

        if (size > 100) {
            size = 100; // Giới hạn tối đa 100 items per page
        }

        Pageable pageable = PageRequest.of(page, size);
        Page<TrackCommentResponse> response = trackCommentService.getRootCommentsByClientDelivery(
                authentication, deliveryId, pageable);

        return ApiResponse.<Page<TrackCommentResponse>>builder()
                .code(HttpStatus.OK.value())
                .message("Lấy danh sách comment trong Client Room thành công")
                .result(response)
                .build();
    }

    /**
     * Lấy danh sách reply của một comment trong Client Room
     */
    @GetMapping("/client-deliveries/comments/{commentId}/replies")
    public ApiResponse<List<TrackCommentResponse>> getClientRoomReplies(
            @PathVariable Long commentId,
            Authentication authentication) {

        if (commentId == null || commentId <= 0) {
            throw new AppException(ErrorCode.INVALID_PARAMETER_FORMAT, "Comment ID không hợp lệ");
        }

        List<TrackCommentResponse> response = trackCommentService.getClientRoomRepliesByComment(
                authentication, commentId);

        return ApiResponse.<List<TrackCommentResponse>>builder()
                .code(HttpStatus.OK.value())
                .message("Lấy danh sách reply trong Client Room thành công")
                .result(response)
                .build();
    }

    /**
     * Lấy comment tại một timestamp cụ thể trong Client Room
     */
    @GetMapping("/client-deliveries/{deliveryId}/comments/by-timestamp")
    public ApiResponse<List<TrackCommentResponse>> getClientRoomCommentsByTimestamp(
            @PathVariable Long deliveryId,
            @RequestParam Integer timestamp,
            Authentication authentication) {

        if (deliveryId == null || deliveryId <= 0) {
            throw new AppException(ErrorCode.INVALID_PARAMETER_FORMAT, "Delivery ID không hợp lệ");
        }

        if (timestamp == null || timestamp < 0) {
            throw new AppException(ErrorCode.INVALID_PARAMETER_FORMAT, "Timestamp không hợp lệ");
        }

        List<TrackCommentResponse> response = trackCommentService.getClientRoomCommentsByTimestamp(
                authentication, deliveryId, timestamp);

        return ApiResponse.<List<TrackCommentResponse>>builder()
                .code(HttpStatus.OK.value())
                .message("Lấy comment theo timestamp trong Client Room thành công")
                .result(response)
                .build();
    }

    /**
     * Lấy thống kê comment trong Client Room theo status
     */
    @GetMapping("/client-deliveries/{deliveryId}/comments/statistics")
    public ApiResponse<TrackCommentStatisticsResponse> getClientRoomCommentStatistics(
            @PathVariable Long deliveryId,
            Authentication authentication) {

        if (deliveryId == null || deliveryId <= 0) {
            throw new AppException(ErrorCode.INVALID_PARAMETER_FORMAT, "Delivery ID không hợp lệ");
        }

        TrackCommentStatisticsResponse response = trackCommentService.getClientRoomCommentStatistics(
                authentication, deliveryId);

        return ApiResponse.<TrackCommentStatisticsResponse>builder()
                .code(HttpStatus.OK.value())
                .message("Lấy thống kê comment trong Client Room thành công")
                .result(response)
                .build();
    }
}



