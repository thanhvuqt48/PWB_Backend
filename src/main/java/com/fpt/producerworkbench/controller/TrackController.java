package com.fpt.producerworkbench.controller;

import com.fpt.producerworkbench.dto.request.TrackUploadCompleteRequest;
import com.fpt.producerworkbench.dto.request.TrackUploadUrlRequest;
import com.fpt.producerworkbench.dto.response.ApiResponse;
import com.fpt.producerworkbench.dto.response.TrackSuggestionResponse;
import com.fpt.producerworkbench.dto.response.TrackUploadUrlResponse;
import com.fpt.producerworkbench.entity.User;
import com.fpt.producerworkbench.exception.AppException;
import com.fpt.producerworkbench.exception.ErrorCode;
import com.fpt.producerworkbench.repository.UserRepository;
import com.fpt.producerworkbench.service.TrackService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/tracks")
@RequiredArgsConstructor
public class TrackController {

    private final TrackService trackService;
    private final UserRepository userRepo;

    private Long resolveUserIdFromJwt(Jwt jwt) {
        String email = jwt.getClaimAsString("email");
        if (email == null || email.isBlank()) email = jwt.getSubject();
        User u = userRepo.findByEmail(email)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        return u.getId();
    }

    @PostMapping("/generate-upload-url")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<TrackUploadUrlResponse> generatePutUrl(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody TrackUploadUrlRequest req) {

        Long userId = resolveUserIdFromJwt(jwt);
        var res = trackService.generateUploadUrl(userId, req);
        return ApiResponse.<TrackUploadUrlResponse>builder().result(res).build();
    }

    @PostMapping("/upload-complete")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Long> uploadComplete(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody TrackUploadCompleteRequest req) {

        Long userId = resolveUserIdFromJwt(jwt);
        Long trackId = trackService.uploadComplete(userId, req);
        return ApiResponse.<Long>builder().result(trackId).message("Transcribing...").build();
    }

    @GetMapping("/{trackId}/suggestion")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<TrackSuggestionResponse> getSuggestion(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long trackId) {
        Long userId = resolveUserIdFromJwt(jwt);
        TrackSuggestionResponse res = trackService.getSuggestion(userId, trackId);
        return ApiResponse.<TrackSuggestionResponse>builder().result(res).build();
    }

    @PostMapping("/{trackId}/resuggest")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> resuggest(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long trackId) {
        Long userId = resolveUserIdFromJwt(jwt);
        trackService.resuggest(userId, trackId);
        return ApiResponse.<Void>builder().message("Re-suggest triggered").build();
    }

    @DeleteMapping("/{trackId}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> deleteTrack(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long trackId) {
        Long userId = resolveUserIdFromJwt(jwt);
        trackService.deleteTrack(userId, trackId);
        return ApiResponse.<Void>builder().message("Track deleted").build();
    }
}
