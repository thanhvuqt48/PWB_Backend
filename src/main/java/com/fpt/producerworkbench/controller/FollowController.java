package com.fpt.producerworkbench.controller;

import com.fpt.producerworkbench.dto.response.ApiResponse;
import com.fpt.producerworkbench.dto.response.FollowListResponse;
import com.fpt.producerworkbench.dto.response.FollowResponse;
import com.fpt.producerworkbench.service.FollowService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RequestMapping("/api/v1/users")
@Slf4j
public class FollowController {

    FollowService followService;

    @PreAuthorize("isAuthenticated()")
    @PostMapping("/{targetId}/follow")
    public ApiResponse<Void> follow(@PathVariable Long targetId, Authentication auth) {
        followService.follow(auth, targetId);
        return ApiResponse.<Void>builder()
                .code(HttpStatus.OK.value())
                .message("Theo dõi thành công")
                .build();
    }

    @PreAuthorize("isAuthenticated()")
    @DeleteMapping("/{targetId}/follow")
    public ApiResponse<Void> unfollow(@PathVariable Long targetId, Authentication auth) {
        followService.unfollow(auth, targetId);
        return ApiResponse.<Void>builder()
                .code(HttpStatus.OK.value())
                .message("Hủy theo dõi thành công")
                .build();
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{targetId}/follow/status")
    public ApiResponse<java.util.Map<String, Boolean>> isFollowing(@PathVariable Long targetId, Authentication auth) {
        boolean following = followService.isFollowing(auth, targetId);
        return ApiResponse.<java.util.Map<String, Boolean>>builder()
                .code(HttpStatus.OK.value())
                .result(java.util.Map.of("following", following))
                .build();
    }

    // Trả về PAGE + totalFollowers + totalFollowing (không cần /follows/summary nữa)
    @GetMapping("/{userId}/followers")
    public ApiResponse<FollowListResponse> followers(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        var result = followService.getFollowersWithTotals(userId, PageRequest.of(page, size));
        return ApiResponse.<FollowListResponse>builder()
                .code(HttpStatus.OK.value())
                .result(result)
                .build();
    }

    // Trả về PAGE + totalFollowers + totalFollowing (không cần /follows/summary nữa)
    @GetMapping("/{userId}/following")
    public ApiResponse<FollowListResponse> following(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        var result = followService.getFollowingWithTotals(userId, PageRequest.of(page, size));
        return ApiResponse.<FollowListResponse>builder()
                .code(HttpStatus.OK.value())
                .result(result)
                .build();
    }
}
