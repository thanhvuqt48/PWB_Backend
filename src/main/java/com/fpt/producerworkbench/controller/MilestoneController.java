package com.fpt.producerworkbench.controller;

import com.fpt.producerworkbench.dto.request.AddMilestoneMemberRequest;
import com.fpt.producerworkbench.dto.request.MilestoneRequest;
import com.fpt.producerworkbench.dto.response.ApiResponse;
import com.fpt.producerworkbench.dto.response.AvailableProjectMemberResponse;
import com.fpt.producerworkbench.dto.response.MilestoneListResponse;
import com.fpt.producerworkbench.dto.response.MilestoneResponse;
import com.fpt.producerworkbench.dto.response.MilestoneDetailResponse;
import com.fpt.producerworkbench.exception.AppException;
import com.fpt.producerworkbench.exception.ErrorCode;
import com.fpt.producerworkbench.service.MilestoneService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
public class MilestoneController {

    private final MilestoneService milestoneService;

    @GetMapping("/{projectId}/milestones")
    public ApiResponse<List<MilestoneListResponse>> getAllMilestonesByProject(
            @PathVariable Long projectId,
            Authentication authentication) {
        if (projectId == null || projectId <= 0) {
            throw new AppException(ErrorCode.INVALID_PARAMETER_FORMAT);
        }

        List<MilestoneListResponse> milestones = milestoneService.getAllMilestonesByProject(projectId, authentication);

        return ApiResponse.<List<MilestoneListResponse>>builder()
                .code(HttpStatus.OK.value())
                .message("Lấy danh sách cột mốc thành công")
                .result(milestones)
                .build();
    }

    @GetMapping("/{projectId}/milestones/{milestonkieId}")
    public ApiResponse<MilestoneDetailResponse> getMilestoneDetail(
            @PathVariable Long projectId,
            @PathVariable Long milestoneId,
            Authentication authentication) {
        if (projectId == null || projectId <= 0 || milestoneId == null || milestoneId <= 0) {
            throw new AppException(ErrorCode.INVALID_PARAMETER_FORMAT);
        }

        MilestoneDetailResponse detail = milestoneService.getMilestoneDetail(projectId, milestoneId, authentication);

        return ApiResponse.<MilestoneDetailResponse>builder()
                .code(HttpStatus.OK.value())
                .message("Lấy chi tiết cột mốc thành công")
                .result(detail)
                .build();
    }

    @PostMapping("/{projectId}/milestones")
    public ApiResponse<MilestoneResponse> createMilestone(
            @PathVariable Long projectId,
            @Valid @RequestBody MilestoneRequest request,
            Authentication authentication) {
        if (projectId == null || projectId <= 0) {
            throw new AppException(ErrorCode.INVALID_PARAMETER_FORMAT);
        }

        MilestoneResponse milestone = milestoneService.createMilestone(projectId, request, authentication);

        return ApiResponse.<MilestoneResponse>builder()
                .code(HttpStatus.CREATED.value())
                .message("Tạo cột mốc thành công")
                .result(milestone)
                .build();
    }

    @GetMapping("/{projectId}/milestones/{milestoneId}/available-members")
    public ApiResponse<List<AvailableProjectMemberResponse>> getAvailableProjectMembers(
            @PathVariable Long projectId,
            @PathVariable Long milestoneId,
            Authentication authentication) {
        if (projectId == null || projectId <= 0 || milestoneId == null || milestoneId <= 0) {
            throw new AppException(ErrorCode.INVALID_PARAMETER_FORMAT);
        }

        List<AvailableProjectMemberResponse> availableMembers = 
                milestoneService.getAvailableProjectMembers(projectId, milestoneId, authentication);

        return ApiResponse.<List<AvailableProjectMemberResponse>>builder()
                .code(HttpStatus.OK.value())
                .message("Lấy danh sách thành viên có thể thêm vào cột mốc thành công")
                .result(availableMembers)
                .build();
    }

    @PostMapping("/{projectId}/milestones/{milestoneId}/members")
    public ApiResponse<MilestoneDetailResponse> addMembersToMilestone(
            @PathVariable Long projectId,
            @PathVariable Long milestoneId,
            @Valid @RequestBody AddMilestoneMemberRequest request,
            Authentication authentication) {
        if (projectId == null || projectId <= 0 || milestoneId == null || milestoneId <= 0) {
            throw new AppException(ErrorCode.INVALID_PARAMETER_FORMAT);
        }

        MilestoneDetailResponse milestoneDetail = 
                milestoneService.addMembersToMilestone(projectId, milestoneId, request, authentication);

        return ApiResponse.<MilestoneDetailResponse>builder()
                .code(HttpStatus.OK.value())
                .message("Thêm thành viên vào cột mốc thành công")
                .result(milestoneDetail)
                .build();
    }
}

