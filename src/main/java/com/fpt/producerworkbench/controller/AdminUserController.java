package com.fpt.producerworkbench.controller;

import com.fpt.producerworkbench.common.UserRole;
import com.fpt.producerworkbench.common.UserStatus;
import com.fpt.producerworkbench.dto.response.ApiResponse;
import com.fpt.producerworkbench.dto.response.PageResponse;
import com.fpt.producerworkbench.dto.response.UserDetailResponse;
import com.fpt.producerworkbench.dto.response.UserListItemResponse;
import com.fpt.producerworkbench.service.UserManagementService;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.AccessLevel;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AdminUserController {

    UserManagementService userManagementService;

    @GetMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<ApiResponse<PageResponse<UserListItemResponse>>> getUsers(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String direction,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) UserRole role,
            @RequestParam(required = false) UserStatus status
    ) {
        PageResponse<UserListItemResponse> data = userManagementService.getUsers(
                page, size, sortBy, direction, keyword, role, status
        );

        ApiResponse<PageResponse<UserListItemResponse>> body = ApiResponse.<PageResponse<UserListItemResponse>>builder()
                .code(200)
                .message("Lấy danh sách người dùng thành công")
                .result(data)
                .build();

        return ResponseEntity.ok(body);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<ApiResponse<UserDetailResponse>> getUserDetail(@PathVariable Long id) {
        UserDetailResponse data = userManagementService.getUserDetail(id);

        ApiResponse<UserDetailResponse> body = ApiResponse.<UserDetailResponse>builder()
                .code(200)
                .message("Lấy chi tiết người dùng thành công")
                .result(data)
                .build();

        return ResponseEntity.ok(body);
    }

    @PutMapping("/{id}/activate")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<ApiResponse<UserDetailResponse>> activate(@PathVariable Long id) {
        UserDetailResponse data = userManagementService.activateUser(id);
        return ResponseEntity.ok(ApiResponse.<UserDetailResponse>builder()
                .code(200)
                .message("Kích hoạt người dùng thành công")
                .result(data)
                .build());
    }

    @PutMapping("/{id}/deactivate")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<ApiResponse<UserDetailResponse>> deactivate(@PathVariable Long id) {
        UserDetailResponse data = userManagementService.deactivateUser(id);
        return ResponseEntity.ok(ApiResponse.<UserDetailResponse>builder()
                .code(200)
                .message("Vô hiệu hóa người dùng thành công")
                .result(data)
                .build());
    }

}
