package com.fpt.producerworkbench.service;

import com.fpt.producerworkbench.dto.request.*;
import com.fpt.producerworkbench.dto.response.*;
import jakarta.mail.MessagingException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.List;

public interface UserService {

    UserResponse createUser(UserCreationRequest request, String otp);

    void sendOtpForgotPassword(EmailRequest request) throws MessagingException, UnsupportedEncodingException;

    void sendOtpRegister(EmailRequest request) throws MessagingException, UnsupportedEncodingException;

    VerifyOtpResponse verifyOtp(VerifyOtpRequest request);

    void resetPassword(PasswordCreationRequest request);

    ChangePasswordResponse changePassword(ChangePasswordRequest request);

    UserProfileResponse getPersonalProfile();

    UserProfileResponse updatePersonalProfile(UpdatePersonalProfileRequest request, MultipartFile avatar);

    List<ParticipantInfoDetailResponse> searchUser(String username);

    CccdInfoResponse verifyCccd(MultipartFile front, MultipartFile back, MultipartFile face) throws IOException;
}
