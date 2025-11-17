package com.fpt.producerworkbench.controller;

import com.fpt.producerworkbench.dto.request.TrackUploadCompleteRequest;
import com.fpt.producerworkbench.dto.request.TrackUploadUrlRequest;
import com.fpt.producerworkbench.dto.request.CompleteAndSuggestRequest;
import com.fpt.producerworkbench.dto.response.*;
import com.fpt.producerworkbench.entity.User;
import com.fpt.producerworkbench.exception.AppException;
import com.fpt.producerworkbench.exception.ErrorCode;
import com.fpt.producerworkbench.repository.UserRepository;
import com.fpt.producerworkbench.service.TrackService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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

    @PostMapping(
            value = "/upload-direct",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<TrackUploadDirectResponse> uploadDirectMultipart(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam("projectId") Long projectId,
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "mimeType", required = false) String mimeType
    ) {
        Long userId = resolveUserIdFromJwt(jwt);
        var res = trackService.uploadDirect(userId, projectId, file, mimeType);
        return ApiResponse.<TrackUploadDirectResponse>builder()
                .result(res).message("Uploaded, transcribing...")
                .build();
    }

    @PostMapping(
            value = "/upload-direct",
            consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE
    )
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<TrackUploadDirectResponse> uploadDirectOctet(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam("projectId") Long projectId,
            @RequestParam(value = "filename", required = false) String filenameParam,
            @RequestParam(value = "mimeType", required = false) String mimeTypeParam,
            @RequestHeader(value = "X-Filename", required = false) String filenameHeader,
            @RequestHeader(value = "X-Mime-Type", required = false) String mimeTypeHeader,
            @RequestBody byte[] body
    ) {
        Long userId = resolveUserIdFromJwt(jwt);
        String filename = (filenameParam != null && !filenameParam.isBlank())
                ? filenameParam : (filenameHeader != null ? filenameHeader : "upload.bin");
        String mimeType = (mimeTypeParam != null && !mimeTypeParam.isBlank())
                ? mimeTypeParam : (mimeTypeHeader != null ? mimeTypeHeader : "application/octet-stream");

        var res = trackService.uploadDirectRaw(userId, projectId, body, filename, mimeType);
        return ApiResponse.<TrackUploadDirectResponse>builder()
                .result(res).message("Uploaded, transcribing...")
                .build();
    }

    @PostMapping("/{trackId}/complete-and-suggest")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<TrackSuggestionResponse> completeAndSuggest(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long trackId,
            @RequestBody(required = false) CompleteAndSuggestRequest body
    ) {
        Long userId = resolveUserIdFromJwt(jwt);
        int wait = (body != null && body.getWaitSeconds() != null) ? body.getWaitSeconds() : 0;
        var res = trackService.completeAndSuggest(userId, trackId, wait);
        return ApiResponse.<TrackSuggestionResponse>builder()
                .result(res)
                .message(res.getAiSuggestions() != null ? "OK" : "Processing")
                .build();
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