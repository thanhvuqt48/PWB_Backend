package com.fpt.producerworkbench.service;

import com.fpt.producerworkbench.dto.request.*;
import com.fpt.producerworkbench.dto.response.ChangePasswordResponse;
import com.fpt.producerworkbench.dto.response.ParticipantInfoDetailResponse;
import com.fpt.producerworkbench.dto.response.UserResponse;
import com.fpt.producerworkbench.dto.response.VerifyOtpResponse;
import jakarta.mail.MessagingException;

import java.io.UnsupportedEncodingException;
import java.util.List;

public interface UserService {

    UserResponse createUser(UserCreationRequest request, String otp);

    void sendOtpForgotPassword(EmailRequest request) throws MessagingException, UnsupportedEncodingException;

    void sendOtpRegister(EmailRequest request) throws MessagingException, UnsupportedEncodingException;

    VerifyOtpResponse verifyOtp(VerifyOtpRequest request);

    void resetPassword(PasswordCreationRequest request);

    ChangePasswordResponse changePassword(ChangePasswordRequest request);

    List<ParticipantInfoDetailResponse> searchUser(String username);
}
