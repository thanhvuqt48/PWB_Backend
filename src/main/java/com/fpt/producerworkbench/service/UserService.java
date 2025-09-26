package com.fpt.producerworkbench.service;

import com.fpt.producerworkbench.dto.request.EmailRequest;
import com.fpt.producerworkbench.dto.request.PasswordCreationRequest;
import com.fpt.producerworkbench.dto.request.UserCreationRequest;
import com.fpt.producerworkbench.dto.request.VerifyOtpRequest;
import com.fpt.producerworkbench.dto.response.UserResponse;
import com.fpt.producerworkbench.dto.response.VerifyOtpResponse;
import jakarta.mail.MessagingException;

import java.io.UnsupportedEncodingException;

public interface UserService {

    UserResponse createUser(UserCreationRequest request, String otp);

    void sendOtpForgotPassword(EmailRequest request) throws MessagingException, UnsupportedEncodingException;

    void sendOtpRegister(EmailRequest request) throws MessagingException, UnsupportedEncodingException;

    VerifyOtpResponse verifyOtp(VerifyOtpRequest request);

    void resetPassword(PasswordCreationRequest request);
}
