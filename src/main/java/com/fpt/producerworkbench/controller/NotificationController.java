package com.fpt.producerworkbench.controller;

import com.fpt.producerworkbench.dto.response.ApiResponse;
import com.fpt.producerworkbench.dto.response.NotificationResponse;
import com.fpt.producerworkbench.dto.response.PageResponse;
import com.fpt.producerworkbench.exception.AppException;
import com.fpt.producerworkbench.exception.ErrorCode;
import com.fpt.producerworkbench.repository.UserRepository;
import com.fpt.producerworkbench.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final UserRepository userRepository;

    @GetMapping
    public ApiResponse<PageResponse<NotificationResponse>> getNotifications(
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            Authentication authentication) {
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        var user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<NotificationResponse> notifications = notificationService.getUserNotifications(user.getId(), pageable);

        return ApiResponse.<PageResponse<NotificationResponse>>builder()
                .code(HttpStatus.OK.value())
                .message("Lấy danh sách thông báo thành công")
                .result(PageResponse.fromPage(notifications))
                .build();
    }

    @GetMapping("/unread-count")
    public ApiResponse<Long> getUnreadCount(Authentication authentication) {
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        var user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        Long count = notificationService.getUnreadCount(user.getId());

        return ApiResponse.<Long>builder()
                .code(HttpStatus.OK.value())
                .message("Lấy số lượng thông báo chưa đọc thành công")
                .result(count)
                .build();
    }

    @PutMapping("/{notificationId}/read")
    public ApiResponse<Void> markAsRead(
            @PathVariable Long notificationId,
            Authentication authentication) {
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        var user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        notificationService.markAsRead(notificationId, user.getId());

        return ApiResponse.<Void>builder()
                .code(HttpStatus.OK.value())
                .message("Đánh dấu thông báo đã đọc thành công")
                .build();
    }

    @PutMapping("/read-all")
    public ApiResponse<Void> markAllAsRead(Authentication authentication) {
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        var user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        notificationService.markAllAsRead(user.getId());

        return ApiResponse.<Void>builder()
                .code(HttpStatus.OK.value())
                .message("Đánh dấu tất cả thông báo đã đọc thành công")
                .build();
    }
}

