package com.fpt.producerworkbench.controller;

import com.fpt.producerworkbench.dto.request.TrackCreateRequest;
import com.fpt.producerworkbench.dto.request.TrackStatusUpdateRequest;
import com.fpt.producerworkbench.dto.request.TrackUpdateRequest;
import com.fpt.producerworkbench.dto.request.TrackVersionUploadRequest;
import com.fpt.producerworkbench.dto.response.ApiResponse;
import com.fpt.producerworkbench.dto.response.TrackResponse;
import com.fpt.producerworkbench.dto.response.TrackUploadUrlResponse;
import com.fpt.producerworkbench.exception.AppException;
import com.fpt.producerworkbench.exception.ErrorCode;
import com.fpt.producerworkbench.service.TrackMilestoneService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Controller quản lý tracks (sản phẩm nhạc) trong phòng nội bộ.
 * Bao gồm: tạo track mới với presigned URL upload, upload version mới, hoàn tất upload và xử lý audio,
 * xem danh sách và chi tiết track, cập nhật thông tin và trạng thái track, xóa track,
 * và lấy HLS playback URL để phát track preview.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class TrackMilestoneController {

    private final TrackMilestoneService trackService;

    /**
     * Tạo track mới và nhận presigned URL để upload file master
     */
    @PostMapping("/projects/{projectId}/milestones/{milestoneId}/tracks")
    public ApiResponse<TrackUploadUrlResponse> createTrack(
            @PathVariable Long projectId,
            @PathVariable Long milestoneId,
            @Valid @RequestBody TrackCreateRequest request,
            Authentication authentication) {

        if (projectId == null || projectId <= 0) {
            throw new AppException(ErrorCode.INVALID_PARAMETER_FORMAT, "Project ID không hợp lệ");
        }
        if (milestoneId == null || milestoneId <= 0) {
            throw new AppException(ErrorCode.INVALID_PARAMETER_FORMAT, "Milestone ID không hợp lệ");
        }

        TrackUploadUrlResponse response = trackService.createTrack(authentication, projectId, milestoneId, request);

        return ApiResponse.<TrackUploadUrlResponse>builder()
                .code(HttpStatus.CREATED.value())
                .message("Đã tạo track thành công. Vui lòng upload file master.")
                .result(response)
                .build();
    }

    /**
     * Upload version mới của một track hiện có
     * Version sẽ tự động tăng dựa trên các version hiện có
     */
    @PostMapping("/tracks/{trackId}/versions")
    public ApiResponse<TrackUploadUrlResponse> uploadNewVersion(
            @PathVariable Long trackId,
            @Valid @RequestBody TrackVersionUploadRequest request,
            Authentication authentication) {

        if (trackId == null || trackId <= 0) {
            throw new AppException(ErrorCode.INVALID_PARAMETER_FORMAT, "Track ID không hợp lệ");
        }

        TrackUploadUrlResponse response = trackService.uploadNewVersion(authentication, trackId, request);

        return ApiResponse.<TrackUploadUrlResponse>builder()
                .code(HttpStatus.CREATED.value())
                .message("Đã tạo version mới thành công. Vui lòng upload file master.")
                .result(response)
                .build();
    }

    /**
     * Hoàn tất upload và trigger xử lý audio
     */
    @PostMapping("/tracks/{trackId}/finalize")
    public ApiResponse<Void> finalizeUpload(
            @PathVariable Long trackId,
            Authentication authentication) {

        if (trackId == null || trackId <= 0) {
            throw new AppException(ErrorCode.INVALID_PARAMETER_FORMAT, "Track ID không hợp lệ");
        }

        trackService.finalizeUpload(authentication, trackId);

        return ApiResponse.<Void>builder()
                .code(HttpStatus.OK.value())
                .message("Đã hoàn tất upload. Hệ thống đang xử lý audio.")
                .build();
    }

    /**
     * Lấy danh sách tracks của một milestone
     */
    @GetMapping("/milestones/{milestoneId}/tracks")
    public ApiResponse<List<TrackResponse>> getTracksByMilestone(
            @PathVariable Long milestoneId,
            Authentication authentication) {

        if (milestoneId == null || milestoneId <= 0) {
            throw new AppException(ErrorCode.INVALID_PARAMETER_FORMAT, "Milestone ID không hợp lệ");
        }

        List<TrackResponse> tracks = trackService.getTracksByMilestone(authentication, milestoneId);

        return ApiResponse.<List<TrackResponse>>builder()
                .code(HttpStatus.OK.value())
                .message("Lấy danh sách tracks thành công")
                .result(tracks)
                .build();
    }

    /**
     * Lấy chi tiết một track
     */
    @GetMapping("/tracks/{trackId}")
    public ApiResponse<TrackResponse> getTrackById(
            @PathVariable Long trackId,
            Authentication authentication) {

        if (trackId == null || trackId <= 0) {
            throw new AppException(ErrorCode.INVALID_PARAMETER_FORMAT, "Track ID không hợp lệ");
        }

        TrackResponse track = trackService.getTrackById(authentication, trackId);

        return ApiResponse.<TrackResponse>builder()
                .code(HttpStatus.OK.value())
                .message("Lấy thông tin track thành công")
                .result(track)
                .build();
    }

    /**
     * Cập nhật thông tin track
     */
    @PutMapping("/tracks/{trackId}")
    public ApiResponse<TrackResponse> updateTrack(
            @PathVariable Long trackId,
            @Valid @RequestBody TrackUpdateRequest request,
            Authentication authentication) {

        if (trackId == null || trackId <= 0) {
            throw new AppException(ErrorCode.INVALID_PARAMETER_FORMAT, "Track ID không hợp lệ");
        }

        TrackResponse track = trackService.updateTrack(authentication, trackId, request);

        return ApiResponse.<TrackResponse>builder()
                .code(HttpStatus.OK.value())
                .message("Cập nhật track thành công")
                .result(track)
                .build();
    }

    /**
     * Chủ dự án cập nhật trạng thái track (có thể chuyển đổi tự do giữa các status)
     * Khi đổi trạng thái sẽ gửi email thông báo cho người chủ track
     */
    @PutMapping("/tracks/{trackId}/status")
    public ApiResponse<TrackResponse> updateTrackStatus(
            @PathVariable Long trackId,
            @Valid @RequestBody TrackStatusUpdateRequest request,
            Authentication authentication) {

        if (trackId == null || trackId <= 0) {
            throw new AppException(ErrorCode.INVALID_PARAMETER_FORMAT, "Track ID không hợp lệ");
        }

        TrackResponse track = trackService.updateTrackStatus(authentication, trackId, request);

        return ApiResponse.<TrackResponse>builder()
                .code(HttpStatus.OK.value())
                .message("Đã cập nhật trạng thái track thành công")
                .result(track)
                .build();
    }

    /**
     * Xóa track
     */
    @DeleteMapping("/tracks/{trackId}")
    public ApiResponse<Void> deleteTrack(
            @PathVariable Long trackId,
            Authentication authentication) {

        if (trackId == null || trackId <= 0) {
            throw new AppException(ErrorCode.INVALID_PARAMETER_FORMAT, "Track ID không hợp lệ");
        }

        trackService.deleteTrack(authentication, trackId);

        return ApiResponse.<Void>builder()
                .code(HttpStatus.OK.value())
                .message("Xóa track thành công")
                .build();
    }

    /**
     * Lấy HLS playback URL để phát track preview
     */
    @GetMapping("/tracks/{trackId}/playback-url")
    public ApiResponse<Map<String, String>> getPlaybackUrl(
            @PathVariable Long trackId,
            Authentication authentication) {

        if (trackId == null || trackId <= 0) {
            throw new AppException(ErrorCode.INVALID_PARAMETER_FORMAT, "Track ID không hợp lệ");
        }

        String playbackUrl = trackService.getPlaybackUrl(authentication, trackId);

        Map<String, String> result = new HashMap<>();
        result.put("playbackUrl", playbackUrl);
        result.put("type", "hls");

        return ApiResponse.<Map<String, String>>builder()
                .code(HttpStatus.OK.value())
                .message("Lấy playback URL thành công")
                .result(result)
                .build();
    }
}




