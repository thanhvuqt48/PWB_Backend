package com.fpt.producerworkbench.controller;

import com.fpt.producerworkbench.dto.request.*;
import com.fpt.producerworkbench.dto.response.*;
import com.fpt.producerworkbench.entity.User;
import com.fpt.producerworkbench.service.UserService;
import jakarta.mail.MessagingException;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.UnsupportedEncodingException;
import java.util.List;

@RestController
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RequestMapping("/api/v1/users")
public class UserController extends User {

    UserService userService;

    @PostMapping("/register")
    public ApiResponse<UserResponse> createUser(@RequestBody @Valid UserCreationRequest request,
                                                @RequestParam String otp) {
        var result = userService.createUser(request, otp);

        return ApiResponse.<UserResponse>builder()
                .code(HttpStatus.CREATED.value())
                .message("Đăng ký tài khoản thành công!")
                .result(result)
                .build();
    }

    @PostMapping("/send-otp-register")
    ApiResponse<Void> sendOtpRegister(@RequestBody EmailRequest request)
            throws MessagingException, UnsupportedEncodingException {
        userService.sendOtpRegister(request);
        return ApiResponse.<Void>builder()
                .code(HttpStatus.OK.value())
                .message("Mã OTP đã được gửi thành công tới email!")
                .build();
    }

    @PostMapping("/send-otp-forgot-password")
    public ApiResponse<Void> sendOtpForgotPassword(@RequestBody EmailRequest request)
            throws MessagingException, UnsupportedEncodingException {

        userService.sendOtpForgotPassword(request);

        return ApiResponse.<Void>builder()
                .code(HttpStatus.OK.value())
                .message("Mã OTP đã được gửi thành công tới email!")
                .build();
    }

    @PostMapping("/verify-otp")
    public ApiResponse<VerifyOtpResponse> verifyOtp(@RequestBody VerifyOtpRequest request) {
        var result = userService.verifyOtp(request);

        return ApiResponse.<VerifyOtpResponse>builder()
                .code(HttpStatus.OK.value())
                .message("Xác thực OTP thành công!")
                .result(result)
                .build();
    }

    @PostMapping("/reset-password")
    ApiResponse<Void> resetPassword(@RequestBody @Valid PasswordCreationRequest request) {
        userService.resetPassword(request);

        return ApiResponse.<Void>builder()
                .code(HttpStatus.OK.value())
                .message("Đặt lại mật khẩu thành công")
                .build();
    }

    @PutMapping("/change-password")
    ApiResponse<ChangePasswordResponse> changePassword(@RequestBody @Valid ChangePasswordRequest request) {
        return ApiResponse.<ChangePasswordResponse>builder()
                .code(HttpStatus.OK.value())
                .result(userService.changePassword(request))
                .build();
    }

    @GetMapping("/profile")
    ApiResponse<UserProfileResponse> getPersonalProfile() {
        return ApiResponse.<UserProfileResponse>builder()
                .code(HttpStatus.OK.value())
                .message("Lấy thông tin cá nhân thành công!")
                .result(userService.getPersonalProfile())
                .build();
    }

    @PutMapping(value = "/profile", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("isAuthenticated()")
    ApiResponse<UserProfileResponse> updatePersonalProfile(
            @RequestPart(value = "avatar", required = false) MultipartFile avatar,
            @Valid @RequestPart("data") UpdatePersonalProfileRequest request) {
        return ApiResponse.<UserProfileResponse>builder()
                .code(HttpStatus.OK.value())
                .message("Cập nhật thông tin cá nhân thành công!")
                .result(userService.updatePersonalProfile(request, avatar))
                .build();
    }

    @GetMapping
    ApiResponse<List<ParticipantInfoDetailResponse>> searchUser(@RequestParam String username) {
        return ApiResponse.<List<ParticipantInfoDetailResponse>>builder()
                .code(HttpStatus.OK.value())
                .result(userService.searchUser(username))
                .build();
    }
}
