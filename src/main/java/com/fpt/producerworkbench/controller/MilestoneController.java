package com.fpt.producerworkbench.controller;

import com.fpt.producerworkbench.dto.request.AddMilestoneMemberRequest;
import com.fpt.producerworkbench.dto.request.CreateMilestoneGroupChatRequest;
import com.fpt.producerworkbench.dto.request.MilestoneRequest;
import com.fpt.producerworkbench.dto.response.ApiResponse;
import com.fpt.producerworkbench.dto.response.ConversationCreationResponse;
import com.fpt.producerworkbench.dto.response.AvailableProjectMemberResponse;
import com.fpt.producerworkbench.dto.response.MilestoneListResponse;
import com.fpt.producerworkbench.dto.response.MilestoneResponse;
import com.fpt.producerworkbench.dto.response.MilestoneDetailResponse;
import com.fpt.producerworkbench.exception.AppException;
import com.fpt.producerworkbench.exception.ErrorCode;
import com.fpt.producerworkbench.common.MilestoneChatType;
import com.fpt.producerworkbench.service.MilestoneService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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

    @GetMapping("/{projectId}/milestones/{milestoneId}")
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

    @PutMapping("/{projectId}/milestones/{milestoneId}")
    public ApiResponse<MilestoneResponse> updateMilestone(
            @PathVariable Long projectId,
            @PathVariable Long milestoneId,
            @Valid @RequestBody MilestoneRequest request,
            Authentication authentication) {
        if (projectId == null || projectId <= 0 || milestoneId == null || milestoneId <= 0) {
            throw new AppException(ErrorCode.INVALID_PARAMETER_FORMAT);
        }

        MilestoneResponse milestone = milestoneService.updateMilestone(projectId, milestoneId, request, authentication);

        return ApiResponse.<MilestoneResponse>builder()
                .code(HttpStatus.OK.value())
                .message("Cập nhật cột mốc thành công")
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

    @DeleteMapping("/{projectId}/milestones/{milestoneId}/members/{userId}")
    public ApiResponse<MilestoneDetailResponse> removeMemberFromMilestone(
            @PathVariable Long projectId,
            @PathVariable Long milestoneId,
            @PathVariable Long userId,
            Authentication authentication) {
        if (projectId == null || projectId <= 0
                || milestoneId == null || milestoneId <= 0
                || userId == null || userId <= 0) {
            throw new AppException(ErrorCode.INVALID_PARAMETER_FORMAT);
        }

        MilestoneDetailResponse milestoneDetail =
                milestoneService.removeMemberFromMilestone(projectId, milestoneId, userId, authentication);

        return ApiResponse.<MilestoneDetailResponse>builder()
                .code(HttpStatus.OK.value())
                .message("Xóa thành viên khỏi cột mốc thành công")
                .result(milestoneDetail)
                .build();
    }

    @DeleteMapping("/{projectId}/milestones/{milestoneId}")
    public ApiResponse<Void> deleteMilestone(
            @PathVariable Long projectId,
            @PathVariable Long milestoneId,
            Authentication authentication) {
        if (projectId == null || projectId <= 0 || milestoneId == null || milestoneId <= 0) {
            throw new AppException(ErrorCode.INVALID_PARAMETER_FORMAT);
        }

        milestoneService.deleteMilestone(projectId, milestoneId, authentication);

        return ApiResponse.<Void>builder()
                .code(HttpStatus.OK.value())
                .message("Xóa cột mốc thành công")
                .build();
    }

    @GetMapping("/{projectId}/milestones/{milestoneId}/group-chats")
    public ApiResponse<List<ConversationCreationResponse>> getGroupChatsForMilestone(
            @PathVariable Long projectId,
            @PathVariable Long milestoneId,
            @RequestParam(required = false) MilestoneChatType type,
            Authentication authentication) {
        if (projectId == null || projectId <= 0 || milestoneId == null || milestoneId <= 0) {
            throw new AppException(ErrorCode.INVALID_PARAMETER_FORMAT);
        }

        List<ConversationCreationResponse> conversations = milestoneService.getGroupChatsForMilestone(
                projectId, milestoneId, type, authentication);

        return ApiResponse.<List<ConversationCreationResponse>>builder()
                .code(HttpStatus.OK.value())
                .message("Lấy danh sách group chat của cột mốc thành công")
                .result(conversations)
                .build();
    }

    @GetMapping("/{projectId}/milestones/{milestoneId}/search-users")
    public ApiResponse<List<AvailableProjectMemberResponse>> searchUsersForMilestoneChat(
            @PathVariable Long projectId,
            @PathVariable Long milestoneId,
            @RequestParam(required = false) String keyword,
            Authentication authentication) {
        if (projectId == null || projectId <= 0 || milestoneId == null || milestoneId <= 0) {
            throw new AppException(ErrorCode.INVALID_PARAMETER_FORMAT);
        }

        List<AvailableProjectMemberResponse> users = milestoneService.searchUsersForMilestoneChat(
                projectId, milestoneId, keyword, authentication);

        return ApiResponse.<List<AvailableProjectMemberResponse>>builder()
                .code(HttpStatus.OK.value())
                .message("Tìm kiếm thành viên thành công")
                .result(users)
                .build();
    }

    @PostMapping(value = "/{projectId}/milestones/{milestoneId}/group-chat", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ConversationCreationResponse> createGroupChatForMilestone(
            @PathVariable Long projectId,
            @PathVariable Long milestoneId,
            @RequestPart(value = "avatar", required = false) MultipartFile avatar,
            @Valid @RequestPart("data") CreateMilestoneGroupChatRequest request,
            Authentication authentication) {
        if (projectId == null || projectId <= 0 || milestoneId == null || milestoneId <= 0) {
            throw new AppException(ErrorCode.INVALID_PARAMETER_FORMAT);
        }

        ConversationCreationResponse conversation = milestoneService.createGroupChatForMilestone(
                projectId, milestoneId, request, avatar, authentication);

        return ApiResponse.<ConversationCreationResponse>builder()
                .code(HttpStatus.CREATED.value())
                .message("Tạo group chat cho cột mốc thành công")
                .result(conversation)
                .build();
    }
}

