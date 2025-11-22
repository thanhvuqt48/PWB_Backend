package com.fpt.producerworkbench.service.impl;

import com.fpt.producerworkbench.common.UserRole;
import com.fpt.producerworkbench.common.UserStatus;
import com.fpt.producerworkbench.dto.event.NotificationEvent;
import com.fpt.producerworkbench.dto.request.*;
import com.fpt.producerworkbench.dto.response.ChangePasswordResponse;
import com.fpt.producerworkbench.dto.response.ParticipantInfoDetailResponse;
import com.fpt.producerworkbench.dto.response.UserProfileResponse;
import com.fpt.producerworkbench.dto.response.UserResponse;
import com.fpt.producerworkbench.dto.response.VerifyOtpResponse;
import com.fpt.producerworkbench.entity.User;
import com.fpt.producerworkbench.exception.AppException;
import com.fpt.producerworkbench.mapper.UserMapper;
import com.fpt.producerworkbench.repository.UserRepository;
import com.fpt.producerworkbench.service.EmailService;
import com.fpt.producerworkbench.service.FileKeyGenerator;
import com.fpt.producerworkbench.service.FileStorageService;
import com.fpt.producerworkbench.service.OtpService;
import com.fpt.producerworkbench.service.UserService;
import com.fpt.producerworkbench.utils.SecurityUtils;
import org.springframework.web.multipart.MultipartFile;
import jakarta.mail.MessagingException;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.UnsupportedEncodingException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import com.fpt.producerworkbench.exception.ErrorCode;

import static com.fpt.producerworkbench.utils.SecurityUtils.generateOtp;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class UserServiceImpl implements UserService {

    UserRepository userRepository;
    UserMapper userMapper;
    PasswordEncoder passwordEncoder;
    EmailService emailService;
    OtpService otpService;
    KafkaTemplate<String, Object> kafkaTemplate;
    FileKeyGenerator fileKeyGenerator;
    FileStorageService fileStorageService;

    @Transactional
    public UserResponse createUser(UserCreationRequest request, String otp) {
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new AppException(ErrorCode.USER_EXISTED);
        }

        String storedOtp = otpService.getOtp(request.getEmail());
        if (storedOtp == null || !storedOtp.equals(otp)) {
            throw new AppException(ErrorCode.OTP_INVALID);
        }

        User user = userMapper.toUser(request);

        user.setPasswordHash(passwordEncoder.encode(user.getPassword()));
        user.setRole(UserRole.CUSTOMER);
        user.setStatus(UserStatus.ACTIVE);
        userRepository.save(user);

        otpService.deleteOtp(request.getEmail());

        NotificationEvent event = NotificationEvent.builder()
                .channel("EMAIL")
                .recipient(user.getEmail())
                .templateCode("welcome-email")
                .subject("Welcome to PWB")
                .build();

        kafkaTemplate.send("notification-delivery", event);

        return userMapper.toUserResponse(user);
    }

    @Transactional
    public void sendOtpForgotPassword(EmailRequest request) throws MessagingException, UnsupportedEncodingException {
        User user = userRepository
                .findByEmail(request.getEmail())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        String otp = generateOtp();
        LocalDateTime expiryDate = LocalDateTime.now().plusMinutes(30);

        user.setOtp(otp);
        user.setOtpExpiryDate(expiryDate);

        String subject = "Your OTP Code";
        String content = String.format(
                "<p>Hello,</p>"
                        + "<p>We received a request to reset your password. Use the following OTP to reset it:</p>"
                        + "<h2>%s</h2>"
                        + "<p>If you did not request this, please ignore this email.</p>"
                        + "<p>Best regards,<br/>Your Company</p>",
                otp);
        emailService.sendEmail(subject, content, List.of(user.getEmail()));
    }

    public void sendOtpRegister(EmailRequest request)
            throws MessagingException, UnsupportedEncodingException {

        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new AppException(ErrorCode.USER_EXISTED);
        }

        String otp = generateOtp();

        otpService.saveOtp(request.getEmail(), otp);

        NotificationEvent event = NotificationEvent.builder()
                .channel("EMAIL")
                .recipient(request.getEmail())
                .templateCode("otp-register-vi")
                .subject("Mã OTP xác thực đăng ký tài khoản")
                .param(new java.util.HashMap<>())
                .build();

        event.getParam().put("recipient", request.getEmail());
        event.getParam().put("otp", otp);
        event.getParam().put("validMinutes", "5");

        kafkaTemplate.send("notification-delivery", event);
    }

    public VerifyOtpResponse verifyOtp(VerifyOtpRequest request) {
        User user = userRepository
                .findByEmail(request.getEmail())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        if (user.getOtp() == null || !user.getOtp().equals(request.getOtp())) {
            return VerifyOtpResponse.builder().valid(false).build();
        }

        if (user.getOtpExpiryDate() == null || user.getOtpExpiryDate().isBefore(LocalDateTime.now())) {
            return VerifyOtpResponse.builder().valid(false).build();
        }

        return VerifyOtpResponse.builder().valid(true).build();
    }

    @Transactional
    public void resetPassword(PasswordCreationRequest request) {
        User user = userRepository
                .findByEmail(request.getEmail())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        if (user.getOtp() == null || !user.getOtp().equals(request.getOtp())) {
            throw new AppException(ErrorCode.TOKEN_INVALID);
        }

        if (user.getOtpExpiryDate() == null || user.getOtpExpiryDate().isBefore(LocalDateTime.now())) {
            throw new AppException(ErrorCode.TOKEN_INVALID);
        }

        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setOtp(null);
        user.setOtpExpiryDate(null);
        userRepository.save(user);
    }

    @Transactional
    @PreAuthorize("isAuthenticated()")
    public ChangePasswordResponse changePassword(ChangePasswordRequest request) {
        String email = SecurityUtils.getCurrentUserLogin()
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHENTICATED));

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword()))
            throw new AppException(ErrorCode.INVALID_OLD_PASSWORD);

        if (passwordEncoder.matches(request.getNewPassword(), user.getPassword())) {
            throw new AppException(ErrorCode.PASSWORD_EXISTED);
        }

        if (!Objects.equals(request.getNewPassword(), request.getConfirmPassword()))
            throw new AppException(ErrorCode.CONFIRM_PASSWORD_INVALID);

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));

        return ChangePasswordResponse
                .builder()
                .message("Change password successful")
                .success(true)
                .build();
    }

    @PreAuthorize("isAuthenticated()")
    public UserProfileResponse getPersonalProfile() {
        String email = SecurityUtils.getCurrentUserLogin()
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHENTICATED));

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        return mapToUserProfileResponse(user);
    }

    @Transactional
    @PreAuthorize("isAuthenticated()")
    public UserProfileResponse updatePersonalProfile(UpdatePersonalProfileRequest request, MultipartFile avatar) {
        String email = SecurityUtils.getCurrentUserLogin()
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHENTICATED));

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        if (avatar != null && !avatar.isEmpty()) {
            log.info("Uploading avatar for user ID: {}", user.getId());

            if (user.getAvatarUrl() != null && !user.getAvatarUrl().isEmpty()) {
                try {
                    String oldKey = extractKeyFromUrl(user.getAvatarUrl());
                    if (oldKey != null) {
                        fileStorageService.deleteFile(oldKey);
                        log.info("Deleted old avatar: {}", oldKey);
                    }
                } catch (Exception e) {
                    log.warn("Failed to delete old avatar: {}", e.getMessage());
                }
            }

            String avatarKey = fileKeyGenerator.generateUserAvatarKey(user.getId(), avatar.getOriginalFilename());
            fileStorageService.uploadFile(avatar, avatarKey);
            String avatarUrl = fileStorageService.generatePermanentUrl(avatarKey);
            user.setAvatarUrl(avatarUrl);
            log.info("Avatar uploaded successfully. Key: {}, URL: {}", avatarKey, avatarUrl);
        } else if (request.getAvatarUrl() != null) {
            user.setAvatarUrl(request.getAvatarUrl());
        }

        if (request.getFirstName() != null) {
            user.setFirstName(request.getFirstName());
        }
        if (request.getLastName() != null) {
            user.setLastName(request.getLastName());
        }
        if (request.getPhoneNumber() != null) {
            user.setPhoneNumber(request.getPhoneNumber());
        }
        if (request.getDateOfBirth() != null) {
            user.setDateOfBirth(request.getDateOfBirth());
        }
        if (request.getLocation() != null) {
            user.setLocation(request.getLocation());
        }

        userRepository.save(user);
        log.info("Personal profile updated for user: {}", email);

        return mapToUserProfileResponse(user);
    }

    private String extractKeyFromUrl(String url) {
        if (url == null || url.isEmpty()) {
            return null;
        }
        // Nếu là CloudFront URL, extract key sau domain
        if (url.contains("cloudfront.net/")) {
            int index = url.indexOf("cloudfront.net/");
            return url.substring(index + "cloudfront.net/".length());
        }
        // Nếu là S3 URL, extract key sau bucket name
        if (url.contains("amazonaws.com/")) {
            String[] parts = url.split("amazonaws.com/");
            if (parts.length > 1) {
                String afterBucket = parts[1];
                // Remove query parameters if any
                int queryIndex = afterBucket.indexOf('?');
                return queryIndex > 0 ? afterBucket.substring(0, queryIndex) : afterBucket;
            }
        }
        // Nếu là key trực tiếp (không có http/https)
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return url;
        }
        return null;
    }

    private UserProfileResponse mapToUserProfileResponse(User user) {
        UserProfileResponse response = new UserProfileResponse();
        response.setEmail(user.getEmail());
        response.setFirstName(user.getFirstName());
        response.setLastName(user.getLastName());
        response.setPhoneNumber(user.getPhoneNumber());
        response.setDateOfBirth(user.getDateOfBirth());
        response.setAvatarUrl(user.getAvatarUrl());
        response.setLocation(user.getLocation());
        response.setRole(user.getRole() != null ? user.getRole().name() : null);
        return response;
    }

    public List<ParticipantInfoDetailResponse> searchUser(String email) {
        return userRepository.findByEmailContaining(email)
                .stream()
                .map(user -> ParticipantInfoDetailResponse.builder()
                        .userId(user.getId())
                        .username(user.getUsername())
                        .avatar(user.getAvatarUrl())
                        .build())
                .toList();
    }
}
