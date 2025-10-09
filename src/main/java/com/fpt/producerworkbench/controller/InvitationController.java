package com.fpt.producerworkbench.controller;

import com.fpt.producerworkbench.dto.request.InvitationRequest;
import com.fpt.producerworkbench.dto.response.ApiResponse;
import com.fpt.producerworkbench.dto.response.InvitationResponse;
import com.fpt.producerworkbench.entity.User;
import com.fpt.producerworkbench.exception.AppException;
import com.fpt.producerworkbench.exception.ErrorCode;
import com.fpt.producerworkbench.repository.UserRepository;
import com.fpt.producerworkbench.service.InvitationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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

    @PostMapping
    @PreAuthorize("hasAnyAuthority('PRODUCER', 'ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, String>>> inviteToProject(
            @PathVariable Long projectId,
            @Valid @RequestBody InvitationRequest request,
            @AuthenticationPrincipal Jwt jwt) {

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
    @PreAuthorize("hasAnyAuthority('PRODUCER', 'ADMIN')")
    public ResponseEntity<ApiResponse<List<InvitationResponse>>> getPendingInvitations(@PathVariable Long projectId, @AuthenticationPrincipal Jwt jwt) {
        User owner = userRepository.findByEmail(jwt.getSubject()).orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        List<InvitationResponse> invitations = invitationService.getPendingInvitationsForProject(projectId, owner);
        return ResponseEntity.ok(ApiResponse.<List<InvitationResponse>>builder()
                .result(invitations)
                .build());
    }

    @DeleteMapping("/{invitationId}")
    @PreAuthorize("hasAnyAuthority('PRODUCER', 'ADMIN')")
    public ResponseEntity<ApiResponse<Void>> cancelInvitation(@PathVariable Long projectId, @PathVariable Long invitationId, @AuthenticationPrincipal Jwt jwt) {
        User owner = userRepository.findByEmail(jwt.getSubject()).orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        invitationService.cancelInvitation(invitationId, owner);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .message("Lời mời đã được hủy thành công.")
                .build());
    }
}