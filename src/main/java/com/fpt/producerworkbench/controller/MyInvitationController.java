package com.fpt.producerworkbench.controller;

import com.fpt.producerworkbench.dto.request.AcceptInvitationRequest;
import com.fpt.producerworkbench.dto.response.ApiResponse;
import com.fpt.producerworkbench.dto.response.InvitationResponse;
import com.fpt.producerworkbench.dto.response.PageResponse;
import com.fpt.producerworkbench.entity.User;
import com.fpt.producerworkbench.exception.AppException;
import com.fpt.producerworkbench.exception.ErrorCode;
import com.fpt.producerworkbench.repository.UserRepository;
import com.fpt.producerworkbench.service.InvitationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/v1/my-invitations")
@RequiredArgsConstructor
public class MyInvitationController {

    private final InvitationService invitationService;
    private final UserRepository userRepository;

    @PostMapping("/accept")
    public ResponseEntity<ApiResponse<Void>> acceptInvitation(
            @Valid @RequestBody AcceptInvitationRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        String userEmail = jwt.getSubject();

        User acceptingUser = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        invitationService.acceptInvitation(request.getToken(), acceptingUser);

        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .message("Lời mời đã được chấp nhận thành công.")
                .build());
    }

    @PostMapping("/{invitationId}/accept")
    public ResponseEntity<ApiResponse<Void>> acceptInvitationById(
            @PathVariable Long invitationId,
            @AuthenticationPrincipal Jwt jwt) {

        String userEmail = jwt.getSubject();

        User acceptingUser = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        invitationService.acceptInvitationById(invitationId, acceptingUser);

        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .message("Lời mời đã được chấp nhận thành công.")
                .build());
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<PageResponse<InvitationResponse>>> getMyInvitations(
            @AuthenticationPrincipal Jwt jwt,
            @PageableDefault(sort = {"createdAt"}, direction = Sort.Direction.DESC) Pageable pageable) {
        User currentUser = userRepository.findByEmail(jwt.getSubject()).orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        PageResponse<InvitationResponse> invitations = invitationService.getMyPendingInvitationsPage(currentUser, pageable);
        return ResponseEntity.ok(ApiResponse.<PageResponse<InvitationResponse>>builder()
                .result(invitations)
                .build());
    }



    @PostMapping("/{invitationId}/decline")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> declineInvitation(@PathVariable Long invitationId, @AuthenticationPrincipal Jwt jwt) {
        User currentUser = userRepository.findByEmail(jwt.getSubject()).orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        invitationService.declineInvitation(invitationId, currentUser);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .message("Bạn đã từ chối lời mời thành công.")
                .build());
    }
}