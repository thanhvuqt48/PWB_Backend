package com.fpt.producerworkbench.controller;

import com.fpt.producerworkbench.dto.request.AddMilestoneMemberRequest;
import com.fpt.producerworkbench.dto.request.MilestoneBriefUpsertRequest;
import com.fpt.producerworkbench.dto.request.MilestoneRequest;
import com.fpt.producerworkbench.dto.response.*;
import com.fpt.producerworkbench.exception.AppException;
import com.fpt.producerworkbench.exception.ErrorCode;
import com.fpt.producerworkbench.service.MilestoneBriefService;
import com.fpt.producerworkbench.dto.request.CreateMilestoneGroupChatRequest;
import com.fpt.producerworkbench.dto.request.DownloadOriginalTracksZipRequest;
import com.fpt.producerworkbench.dto.response.ApiResponse;
import com.fpt.producerworkbench.dto.response.ConversationCreationResponse;
import com.fpt.producerworkbench.dto.response.AvailableProjectMemberResponse;
import com.fpt.producerworkbench.dto.response.MilestoneListResponse;
import com.fpt.producerworkbench.dto.response.MilestoneResponse;
import com.fpt.producerworkbench.dto.response.MilestoneDetailResponse;
import com.fpt.producerworkbench.common.MilestoneChatType;
import com.fpt.producerworkbench.dto.response.DownloadOriginalTracksZipResponse;
import com.fpt.producerworkbench.service.MilestoneService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Controller quản lý các thao tác liên quan đến milestone (cột mốc) của project.
 * Bao gồm: xem danh sách, chi tiết, tạo, cập nhật, xóa milestone, quản lý thành viên milestone,
 * hoàn thành milestone, và tải về ZIP các track bản gốc.
 */
@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
public class MilestoneController {

    private final MilestoneService milestoneService;
    private final MilestoneBriefService milestoneBriefService;

    /**
     * Lấy danh sách tất cả milestone của project.-
     * Yêu cầu đăng nhập và có quyền truy cập project.
     */
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

    /**
     * Lấy thông tin chi tiết của milestone.
     * Yêu cầu đăng nhập và có quyền truy cập project.
     */
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

    /**
     * Tạo milestone mới cho project.
     * Yêu cầu đăng nhập và có quyền quản lý milestone (thường là Owner).
     */
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

    /**
     * Cập nhật thông tin milestone.
     * Yêu cầu đăng nhập và có quyền quản lý milestone (thường là Owner).
     */
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

    /**
     * Lấy danh sách thành viên project có thể thêm vào milestone.
     * Trả về các thành viên chưa có trong milestone và có role phù hợp (COLLABORATOR hoặc OBSERVER).
     */
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

    /**
     * Thêm thành viên vào milestone.
     * Yêu cầu đăng nhập và có quyền quản lý milestone (chỉ Owner). Chỉ có thể thêm COLLABORATOR hoặc OBSERVER.
     */
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

    /**
     * Xóa thành viên khỏi milestone.
     * Yêu cầu đăng nhập và có quyền quản lý milestone (chỉ Owner).
     */
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

    /**
     * Xóa milestone.
     * Yêu cầu đăng nhập và có quyền quản lý milestone (chỉ Owner).
     */
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


    @GetMapping("/{projectId}/milestones/{milestoneId}/brief")
    public ApiResponse<MilestoneBriefDetailResponse> getMilestoneBrief(
            @PathVariable Long projectId,
            @PathVariable Long milestoneId,
            Authentication authentication) {
        if (projectId == null || projectId <= 0 || milestoneId == null || milestoneId <= 0) {
            throw new AppException(ErrorCode.INVALID_PARAMETER_FORMAT);
        }
        MilestoneBriefDetailResponse brief =
                milestoneBriefService.getMilestoneBrief(projectId, milestoneId, authentication);

        return ApiResponse.<MilestoneBriefDetailResponse>builder()
                .code(HttpStatus.OK.value())
                .message("Lấy miêu tả cột mốc thành công")
                .result(brief)
                .build();
    }

    /**
     * Đánh dấu milestone đã hoàn thành.
     * Yêu cầu đăng nhập và có quyền quản lý milestone (chỉ Owner). Tự động gửi email thông báo cho owner.
     */
    @PostMapping("/{projectId}/milestones/{milestoneId}/complete")
    public ApiResponse<MilestoneResponse> completeMilestone(
            @PathVariable Long projectId,
            @PathVariable Long milestoneId,
            Authentication authentication) {
        if (projectId == null || projectId <= 0 || milestoneId == null || milestoneId <= 0) {
            throw new AppException(ErrorCode.INVALID_PARAMETER_FORMAT);
        }
        MilestoneResponse milestone = milestoneService.completeMilestone(projectId, milestoneId, authentication);
        return ApiResponse.<MilestoneResponse>builder()
                .code(HttpStatus.OK.value())
                .message("Chấp nhận hoàn thành cột mốc thành công")
                .result(milestone)
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


    /**
     * Tải về file ZIP chứa các track bản gốc đã gửi cho client trong milestone.
     * Yêu cầu milestone đã hoàn thành (COMPLETED) và có quyền truy cập Client Room.
     * Request body có thể null để tải tất cả tracks, hoặc chỉ định trackIds cụ thể.
     */
    @PostMapping("/{projectId}/milestones/{milestoneId}/download-original-tracks-zip")
    public ApiResponse<DownloadOriginalTracksZipResponse> downloadOriginalTracksZip(
            @PathVariable Long projectId,
            @PathVariable Long milestoneId,
            @Valid @RequestBody(required = false) DownloadOriginalTracksZipRequest request,
            Authentication authentication) {
        if (projectId == null || projectId <= 0 || milestoneId == null || milestoneId <= 0) {
            throw new AppException(ErrorCode.INVALID_PARAMETER_FORMAT);
        }

        if (request == null) {
            request = DownloadOriginalTracksZipRequest.builder().build();
        }

        DownloadOriginalTracksZipResponse response = milestoneService.downloadOriginalTracksZip(
                projectId, milestoneId, request, authentication);

        return ApiResponse.<DownloadOriginalTracksZipResponse>builder()
                .code(HttpStatus.OK.value())
                .message("Tạo file ZIP các track bản gốc thành công")
                .result(response)
                .build();
    }

    @PutMapping("/{projectId}/milestones/{milestoneId}/brief")
    public ApiResponse<MilestoneBriefDetailResponse> upsertMilestoneBrief(
            @PathVariable Long projectId,
            @PathVariable Long milestoneId,
            @Valid @RequestBody MilestoneBriefUpsertRequest request,
            Authentication authentication) {

        if (projectId == null || projectId <= 0 || milestoneId == null || milestoneId <= 0) {
            throw new AppException(ErrorCode.INVALID_PARAMETER_FORMAT);
        }

        MilestoneBriefDetailResponse brief =
                milestoneBriefService.upsertMilestoneBrief(projectId, milestoneId, request, authentication);

        return ApiResponse.<MilestoneBriefDetailResponse>builder()
                .code(HttpStatus.OK.value())
                .message("Lưu miêu tả cột mốc thành công")
                .result(brief)
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

    @DeleteMapping("/{projectId}/milestones/{milestoneId}/brief")
    public ApiResponse<Void> deleteExternalMilestoneBrief(
            @PathVariable Long projectId,
            @PathVariable Long milestoneId,
            Authentication authentication) {

        if (projectId == null || projectId <= 0 || milestoneId == null || milestoneId <= 0) {
            throw new AppException(ErrorCode.INVALID_PARAMETER_FORMAT);
        }

        milestoneBriefService.deleteExternalMilestoneBrief(projectId, milestoneId, authentication);

        return ApiResponse.<Void>builder()
                .code(HttpStatus.OK.value())
                .message("Xóa miêu tả cột mốc EXTERNAL thành công")
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

    @GetMapping("/{projectId}/milestones/{milestoneId}/brief/internal")
    public ApiResponse<MilestoneBriefDetailResponse> getInternalMilestoneBrief(
            @PathVariable Long projectId,
            @PathVariable Long milestoneId,
            Authentication authentication) {

        if (projectId == null || projectId <= 0 || milestoneId == null || milestoneId <= 0) {
            throw new AppException(ErrorCode.INVALID_PARAMETER_FORMAT);
        }

        MilestoneBriefDetailResponse brief =
                milestoneBriefService.getInternalMilestoneBrief(projectId, milestoneId, authentication);

        return ApiResponse.<MilestoneBriefDetailResponse>builder()
                .code(HttpStatus.OK.value())
                .message("Lấy miêu tả cột mốc INTERNAL thành công")
                .result(brief)
                .build();
    }

    @PutMapping("/{projectId}/milestones/{milestoneId}/brief/internal")
    public ApiResponse<MilestoneBriefDetailResponse> upsertInternalMilestoneBrief(
            @PathVariable Long projectId,
            @PathVariable Long milestoneId,
            @Valid @RequestBody MilestoneBriefUpsertRequest request,
            Authentication authentication) {

        if (projectId == null || projectId <= 0 || milestoneId == null || milestoneId <= 0) {
            throw new AppException(ErrorCode.INVALID_PARAMETER_FORMAT);
        }

        MilestoneBriefDetailResponse brief =
                milestoneBriefService.upsertInternalMilestoneBrief(projectId, milestoneId, request, authentication);

        return ApiResponse.<MilestoneBriefDetailResponse>builder()
                .code(HttpStatus.OK.value())
                .message("Lưu miêu tả cột mốc INTERNAL thành công")
                .result(brief)
                .build();
    }

    @DeleteMapping("/{projectId}/milestones/{milestoneId}/brief/internal")
    public ApiResponse<Void> deleteInternalMilestoneBrief(
            @PathVariable Long projectId,
            @PathVariable Long milestoneId,
            Authentication authentication) {

        if (projectId == null || projectId <= 0 || milestoneId == null || milestoneId <= 0) {
            throw new AppException(ErrorCode.INVALID_PARAMETER_FORMAT);
        }

        milestoneBriefService.deleteInternalMilestoneBrief(projectId, milestoneId, authentication);

        return ApiResponse.<Void>builder()
                .code(HttpStatus.OK.value())
                .message("Xóa miêu tả cột mốc INTERNAL thành công")
                .build();
    }

    @PostMapping(value = "/{projectId}/milestones/{milestoneId}/brief/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<String> uploadBriefFile(
            @PathVariable Long projectId,
            @PathVariable Long milestoneId,
            @RequestParam("file") MultipartFile file,
            @RequestParam("type") String type,
            Authentication authentication) {

        if (projectId == null || projectId <= 0 || milestoneId == null || milestoneId <= 0) {
            throw new AppException(ErrorCode.INVALID_PARAMETER_FORMAT);
        }

        String fileKey = milestoneBriefService.uploadBriefFile(projectId, milestoneId, file, type, authentication);

        return ApiResponse.<String>builder()
                .code(HttpStatus.OK.value())
                .message("Upload file thành công")
                .result(fileKey)
                .build();
    }
}

