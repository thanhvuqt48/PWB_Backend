package com.fpt.producerworkbench.controller;

import com.fpt.producerworkbench.common.InvitationStatus;
import com.fpt.producerworkbench.dto.request.InvitationRequest;
import com.fpt.producerworkbench.dto.response.ApiResponse;
import com.fpt.producerworkbench.dto.response.InvitationResponse;
import com.fpt.producerworkbench.dto.response.PageResponse;
import com.fpt.producerworkbench.entity.User;
import com.fpt.producerworkbench.exception.AppException;
import com.fpt.producerworkbench.exception.ErrorCode;
import com.fpt.producerworkbench.repository.UserRepository;
import com.fpt.producerworkbench.service.InvitationService;
import com.fpt.producerworkbench.service.ProjectPermissionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/invitations")
@RequiredArgsConstructor
public class InvitationController {

    private final InvitationService invitationService;
    private final UserRepository userRepository;
    private final ProjectPermissionService projectPermissionService;

    @PostMapping
    public ResponseEntity<ApiResponse<Map<String, String>>> inviteToProject(
            @PathVariable Long projectId,
            @Valid @RequestBody InvitationRequest request,
            @AuthenticationPrincipal Jwt jwt,
            Authentication auth) {

        // Check permissions using service
        var permissions = projectPermissionService.checkProjectPermissions(auth, projectId);
        if (!permissions.isCanInviteMembers()) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }

        String inviterEmail = jwt.getSubject();

        User inviter = userRepository.findByEmail(inviterEmail)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        String invitationLink = invitationService.createInvitation(projectId, request, inviter);

        return ResponseEntity.ok(ApiResponse.<Map<String, String>>builder()
                .message("Liên kết lời mời đã được gửi đến email của người dùng.")
                .result(Map.of("invitationLink", invitationLink))
                .build());
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<InvitationResponse>>> getPendingInvitations(@PathVariable Long projectId, @AuthenticationPrincipal Jwt jwt, Authentication auth) {
        // Check permissions using service
        var permissions = projectPermissionService.checkProjectPermissions(auth, projectId);
        if (!permissions.isCanManageInvitations()) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }

        User owner = userRepository.findByEmail(jwt.getSubject()).orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        List<InvitationResponse> invitations = invitationService.getPendingInvitationsForProject(projectId, owner);
        return ResponseEntity.ok(ApiResponse.<List<InvitationResponse>>builder()
                .result(invitations)
                .build());
    }

    @GetMapping("/my-owned")
    public ResponseEntity<ApiResponse<PageResponse<InvitationResponse>>> getAllOwnedInvitations(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(name = "status", required = false) InvitationStatus status,
            Pageable pageable,
            Authentication auth) {
        // Check permissions - only PRODUCER and ADMIN can view owned invitations
        var permissions = projectPermissionService.checkProjectPermissions(auth, null);
        if (!permissions.isCanCreateProject()) { // Same logic as create project
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }

        User owner = userRepository.findByEmail(jwt.getSubject()).orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        PageResponse<InvitationResponse> page = invitationService.getAllOwnedInvitations(owner, status, pageable);
        return ResponseEntity.ok(ApiResponse.<PageResponse<InvitationResponse>>builder()
                .result(page)
                .build());
    }

    @DeleteMapping("/{invitationId}")
    public ResponseEntity<ApiResponse<Void>> cancelInvitation(@PathVariable Long projectId, @PathVariable Long invitationId, @AuthenticationPrincipal Jwt jwt, Authentication auth) {
        // Check permissions using service
        var permissions = projectPermissionService.checkProjectPermissions(auth, projectId);
        if (!permissions.isCanManageInvitations()) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }

        User owner = userRepository.findByEmail(jwt.getSubject()).orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        invitationService.cancelInvitation(invitationId, owner);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .message("Lời mời đã được hủy thành công.")
                .build());
    }
}