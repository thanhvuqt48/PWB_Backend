package com.fpt.producerworkbench.controller;

import com.fpt.producerworkbench.dto.response.FollowResponse;
import com.fpt.producerworkbench.dto.response.ApiResponse;
import com.fpt.producerworkbench.entity.User;
import com.fpt.producerworkbench.service.FollowService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RequestMapping("/api/v1/users")
@Slf4j
public class FollowController {

    FollowService followService;

    Long currentUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof User u)) {
            throw new IllegalStateException("Chưa xác thực hoặc principal không hợp lệ");
        }
        return u.getId();
    }

    @PostMapping("/{targetId}/follow")
    ApiResponse<Void> follow(@PathVariable Long targetId, Authentication auth) {
        Long me = currentUserId(auth);
        followService.follow(me, targetId);
        return ApiResponse.<Void>builder()
                .code(HttpStatus.OK.value())
                .message("Theo dõi thành công")
                .build();
    }

    @DeleteMapping("/{targetId}/follow")
    ApiResponse<Void> unfollow(@PathVariable Long targetId, Authentication auth) {
        Long me = currentUserId(auth);
        followService.unfollow(me, targetId);
        return ApiResponse.<Void>builder()
                .code(HttpStatus.OK.value())
                .message("Unfollow thành công")
                .build();
    }

    @GetMapping("/{userId}/followers")
    ApiResponse<Page<FollowResponse>> followers(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Page<FollowResponse> result = followService.getFollowers(userId, PageRequest.of(page, size));
        return ApiResponse.<Page<FollowResponse>>builder()
                .code(HttpStatus.OK.value())
                .result(result)
                .build();
    }

    @GetMapping("/{userId}/following")
    ApiResponse<Page<FollowResponse>> following(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Page<FollowResponse> result = followService.getFollowing(userId, PageRequest.of(page, size));
        return ApiResponse.<Page<FollowResponse>>builder()
                .code(HttpStatus.OK.value())
                .result(result)
                .build();
    }

    @GetMapping("/{targetId}/follow/status")
    ApiResponse<Map<String, Boolean>> isFollowing(@PathVariable Long targetId, Authentication auth) {
        Long me = currentUserId(auth);
        boolean following = followService.isFollowing(me, targetId);
        return ApiResponse.<Map<String, Boolean>>builder()
                .code(HttpStatus.OK.value())
                .result(Map.of("following", following))
                .build();
    }

    @GetMapping("/{userId}/follows/summary")
    ApiResponse<Map<String, Long>> summary(@PathVariable Long userId) {
        long followers = followService.countFollowers(userId);
        long following = followService.countFollowing(userId);
        return ApiResponse.<Map<String, Long>>builder()
                .code(HttpStatus.OK.value())
                .result(Map.of("followers", followers, "following", following))
                .build();
    }
}
