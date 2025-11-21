//package com.fpt.producerworkbench.controller;
//
//import com.fpt.producerworkbench.common.InspirationType;
//import com.fpt.producerworkbench.dto.request.InspirationNoteCreateRequest;
//import com.fpt.producerworkbench.dto.response.ApiResponse;
//import com.fpt.producerworkbench.dto.response.InspirationItemResponse;
//import com.fpt.producerworkbench.dto.response.InspirationListResponse;
//import com.fpt.producerworkbench.service.InspirationService;
//import lombok.RequiredArgsConstructor;
//import lombok.experimental.FieldDefaults;
//import org.springframework.data.domain.PageRequest;
//import org.springframework.data.domain.Pageable;
//import org.springframework.security.access.prepost.PreAuthorize;
//import org.springframework.security.core.Authentication;
//import org.springframework.web.bind.annotation.*;
//import org.springframework.web.multipart.MultipartFile;
//
//@RestController
//@RequestMapping("/api/v1/projects/{projectId}/inspirations")
//@RequiredArgsConstructor
//@FieldDefaults(makeFinal = true, level = lombok.AccessLevel.PRIVATE)
//public class InspirationController {
//
//    InspirationService inspirationService;
//
//
//    private Long getCurrentUserId(Authentication auth) {
//        return (Long) auth.getCredentials();
//
//    }
//
//    @PostMapping("/asset")
//    @PreAuthorize("isAuthenticated()")
//    public ApiResponse<InspirationItemResponse> uploadAsset(
//            Authentication auth,
//            @PathVariable Long projectId,
//            @RequestParam InspirationType type,
//            @RequestParam("file") MultipartFile file) {
//
//        Long userId = getCurrentUserId(auth);
//        var result = inspirationService.uploadAsset(projectId, userId, type, file);
//        return ApiResponse.<InspirationItemResponse>builder().result(result).build();
//    }
//
//    @PostMapping("/note")
//    @PreAuthorize("isAuthenticated()")
//    public ApiResponse<InspirationItemResponse> createNote(
//            Authentication auth,
//            @PathVariable Long projectId,
//            @RequestBody InspirationNoteCreateRequest request) {
//
//        Long userId = getCurrentUserId(auth);
//        var result = inspirationService.createNote(projectId, userId, request);
//        return ApiResponse.<InspirationItemResponse>builder().result(result).build();
//    }
//
//    @GetMapping
//    @PreAuthorize("isAuthenticated()")
//    public ApiResponse<InspirationListResponse<InspirationItemResponse>> list(
//            @PathVariable Long projectId,
//            @RequestParam(required = false) InspirationType type,
//            @RequestParam(defaultValue = "0") int page,
//            @RequestParam(defaultValue = "12") int size) {
//
//        Pageable pageable = PageRequest.of(page, size);
//        var result = inspirationService.list(projectId, type, pageable);
//        return ApiResponse.<InspirationListResponse<InspirationItemResponse>>builder().result(result).build();
//    }
//
//    @GetMapping("/{itemId}/view-url")
//    @PreAuthorize("isAuthenticated()")
//    public ApiResponse<String> getViewUrl(@PathVariable Long projectId, @PathVariable Long itemId) {
//        String url = inspirationService.getViewUrl(projectId, itemId);
//        return ApiResponse.<String>builder().result(url).build();
//    }
//
//    @GetMapping("/{itemId}/download-url")
//    @PreAuthorize("isAuthenticated()")
//    public ApiResponse<String> getDownloadUrl(@PathVariable Long projectId,
//                                              @PathVariable Long itemId,
//                                              @RequestParam(required = false) String fileName) {
//        String url = inspirationService.getDownloadUrl(projectId, itemId, fileName);
//        return ApiResponse.<String>builder().result(url).build();
//    }
//
//
//    @DeleteMapping("/{itemId}")
//    @PreAuthorize("isAuthenticated()")
//    public ApiResponse<Void> delete(Authentication auth,
//                                    @PathVariable Long projectId,
//                                    @PathVariable Long itemId) {
//        Long userId = getCurrentUserId(auth);
//        inspirationService.delete(projectId, itemId, userId);
//        return ApiResponse.<Void>builder().message("Đã xóa").build();
//    }
//}
package com.fpt.producerworkbench.controller;

import com.fpt.producerworkbench.common.InspirationType;
import com.fpt.producerworkbench.dto.request.InspirationNoteCreateRequest;
import com.fpt.producerworkbench.dto.response.ApiResponse;
import com.fpt.producerworkbench.dto.response.InspirationItemResponse;
import com.fpt.producerworkbench.dto.response.InspirationListResponse;
import com.fpt.producerworkbench.entity.User;
import com.fpt.producerworkbench.exception.AppException;
import com.fpt.producerworkbench.exception.ErrorCode;
import com.fpt.producerworkbench.repository.UserRepository;
import com.fpt.producerworkbench.service.InspirationService;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/inspirations")
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = lombok.AccessLevel.PRIVATE)
public class InspirationController {

    InspirationService inspirationService;
    UserRepository userRepository;

    private Long resolveUserIdFromJwt(Jwt jwt) {
        String email = jwt.getClaimAsString("email");
        if (email == null || email.isBlank()) {
            String pu = jwt.getClaimAsString("preferred_username");
            if (pu != null && pu.contains("@")) email = pu;
            if ((email == null || email.isBlank()) && jwt.getSubject() != null && jwt.getSubject().contains("@")) {
                email = jwt.getSubject();
            }
        }
        if (email == null || email.isBlank()) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
        User u = userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        return u.getId();
    }

    @PostMapping("/asset")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<InspirationItemResponse> uploadAsset(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long projectId,
            @RequestParam InspirationType type,
            @RequestParam("file") MultipartFile file) {

        Long userId = resolveUserIdFromJwt(jwt);
        var result = inspirationService.uploadAsset(projectId, userId, type, file);
        return ApiResponse.<InspirationItemResponse>builder().result(result).build();
    }

    @PostMapping("/note")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<InspirationItemResponse> createNote(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long projectId,
            @RequestBody InspirationNoteCreateRequest request) {

        Long userId = resolveUserIdFromJwt(jwt);
        var result = inspirationService.createNote(projectId, userId, request);
        return ApiResponse.<InspirationItemResponse>builder().result(result).build();
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<InspirationListResponse<InspirationItemResponse>> list(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long projectId,
            @RequestParam(required = false) InspirationType type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {

        Long userId = resolveUserIdFromJwt(jwt);
        var result = inspirationService.list(projectId, type, PageRequest.of(page, size), userId);
        return ApiResponse.<InspirationListResponse<InspirationItemResponse>>builder().result(result).build();
    }

    @GetMapping("/{itemId}/view-url")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<String> getViewUrl(@AuthenticationPrincipal Jwt jwt,
                                          @PathVariable Long projectId,
                                          @PathVariable Long itemId) {
        Long userId = resolveUserIdFromJwt(jwt);
        String url = inspirationService.getViewUrl(projectId, itemId, userId);
        return ApiResponse.<String>builder().result(url).build();
    }

    @GetMapping("/{itemId}/download-url")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<String> getDownloadUrl(@AuthenticationPrincipal Jwt jwt,
                                              @PathVariable Long projectId,
                                              @PathVariable Long itemId,
                                              @RequestParam(required = false) String fileName) {
        Long userId = resolveUserIdFromJwt(jwt);
        String url = inspirationService.getDownloadUrl(projectId, itemId, fileName, userId);
        return ApiResponse.<String>builder().result(url).build();
    }

    @DeleteMapping("/{itemId}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> delete(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long projectId,
            @PathVariable Long itemId) {

        Long userId = resolveUserIdFromJwt(jwt);
        inspirationService.delete(projectId, itemId, userId);
        return ApiResponse.<Void>builder().message("Đã xóa").build();
    }
}