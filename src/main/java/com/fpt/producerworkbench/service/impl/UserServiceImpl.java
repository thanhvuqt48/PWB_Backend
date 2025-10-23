package com.fpt.producerworkbench.service.impl;


import com.fpt.producerworkbench.common.UserRole;
import com.fpt.producerworkbench.common.UserStatus;
import com.fpt.producerworkbench.dto.event.NotificationEvent;
import com.fpt.producerworkbench.dto.request.*;
import com.fpt.producerworkbench.dto.response.ChangePasswordResponse;
import com.fpt.producerworkbench.dto.response.UserResponse;
import com.fpt.producerworkbench.dto.response.VerifyOtpResponse;
import com.fpt.producerworkbench.entity.User;
import com.fpt.producerworkbench.exception.AppException;
import com.fpt.producerworkbench.mapper.UserMapper;
import com.fpt.producerworkbench.repository.UserRepository;
import com.fpt.producerworkbench.service.EmailService;
import com.fpt.producerworkbench.service.OtpService;
import com.fpt.producerworkbench.service.UserService;
import com.fpt.producerworkbench.utils.SecurityUtils;
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

        String subject = "Mã OTP cho tài khoản Producer Workbench";
        String neonPink = "#FF007F";
        String neonCyan = "#00FFFF";
        StringBuilder content = new StringBuilder();
        content.append("<!DOCTYPE html>")
                .append("<html>")
                .append("<head>")
                .append("<meta charset='UTF-8'>")
                .append("<meta name='viewport' content='width=device-width, initial-scale=1.0'>")
                .append("<title>Mã OTP của bạn</title>")
                // CSS inline cho hiệu ứng text-shadow (Neon Glow) - Hỗ trợ tốt trong Apple Mail/Outlook.com/Gmail(một phần)
                .append("<style>")
                .append(".neon-title { text-shadow: 0 0 5px ").append(neonCyan).append(", 0 0 10px ").append(neonCyan).append(" !important; }")
                .append(".neon-otp { color: ").append(neonPink).append(" !important; text-shadow: 0 0 10px ").append(neonPink).append(", 0 0 20px ").append(neonPink).append(" !important; }")
                .append(".otp-box { box-shadow: 0 0 15px rgba(0, 255, 255, 0.5), inset 0 0 10px rgba(0, 255, 255, 0.3) !important; border: 1px solid ").append(neonCyan).append(" !important; }")
                .append("</style>")
                .append("</head>")

                // Main Content Table for centering and structure
                .append("<table role='presentation' width='100%' cellspacing='0' cellpadding='0' border='0'>")
                .append("<tr>")
                .append("<td align='center' style='padding: 40px 10px;'>")

                // Content Block - Background tối hơn và box-shadow glow
                .append("<table role='presentation' width='100%' style='max-width: 600px; background-color: rgba(10, 0, 20, 0.9); border-radius: 12px; border: 1px solid ").append(neonCyan).append("; box-shadow: 0 0 25px rgba(0, 255, 255, 0.3);' cellspacing='0' cellpadding='0' border='0'>")
                .append("<tr>")
                .append("<td style='padding: 30px;'>")

                // Header - Áp dụng hiệu ứng Neon Title
                .append("<h2 class='neon-title' style='text-align: center; color: ").append(neonCyan).append("; margin-top: 0; font-size: 28px;'>🚀 CHÀO MỪNG ĐẾN VŨ TRỤ PWB!</h2>")
                .append("<p style='text-align: center; font-size: 16px; color: #E0E0E0; margin-top: 25px;'>Xin chào <strong>")
                .append(request.getEmail())
                .append("</strong>,</p>")
                .append("<p style='text-align: center; font-size: 16px; color: #E0E0E0; margin-bottom: 30px;'>Cảm ơn bạn đã đăng ký **Producer Workbench**. Hãy cùng khám phá dải ngân hà âm nhạc!</p>")

                // OTP Block - Áp dụng hiệu ứng Neon Box
                .append("<div class='otp-box' style='text-align: center; margin: 30px auto; padding: 30px; background-color: #0b0c1b; border-radius: 10px; max-width: 250px;'>")
                .append("<p style='font-size: 18px; color: ").append(neonCyan).append("; margin-bottom: 10px; margin-top: 0;'>MÃ OTP CỦA BẠN LÀ:</p>")
                // Áp dụng hiệu ứng Neon OTP
                .append("<p class='neon-otp' style='font-size: 42px; font-weight: bold; margin-top: 5px; margin-bottom: 0; line-height: 1.2;'>")
                .append(otp)
                .append("</p>")
                .append("</div>")

                // Note/Footer
                .append("<p style='text-align: center; font-size: 14px; color: #999; margin-top: 30px;'>**Lưu ý:** Mã OTP này chỉ có hiệu lực trong <strong style='color: ").append(neonPink).append(";'>5 phút</strong>. Vui lòng nhập ngay để hoàn tất đăng ký.</p>")
                .append("<p style='text-align: center; font-size: 14px; color: #999;'>Nếu bạn không yêu cầu mã này, vui lòng bỏ qua email này.</p>")
                .append("<p style='text-align: center; margin-top: 40px; font-size: 16px; color: ").append(neonCyan).append(";'>🎶 HÃY CÙNG BAY TRONG VŨ TRỤ ÂM NHẠC! 🎶</p>")
                .append("<p style='text-align: center; font-weight: bold; color: #E0E0E0;'>ĐỘI NGŨ PWB</p>")

                .append("</td>")
                .append("</tr>")
                .append("</table>") // End Content Block

                .append("</td>")
                .append("</tr>")
                .append("</table>") // End Main Content Table

                .append("</body>")
                .append("</html>");


        String emailContent = content.toString();
        emailService.sendEmail(subject, emailContent, List.of(request.getEmail()));
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
    public ChangePasswordResponse changePassword(ChangePasswordRequest request){
        String email = SecurityUtils.getCurrentUserLogin()
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHENTICATED));

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        if(! passwordEncoder.matches(request.getCurrentPassword(), user.getPassword()))
            throw new AppException(ErrorCode.INVALID_OLD_PASSWORD);

        if (passwordEncoder.matches(request.getNewPassword(), user.getPassword())) {
            throw new AppException(ErrorCode.PASSWORD_EXISTED);
        }

        if(! Objects.equals(request.getNewPassword(), request.getConfirmPassword()))
            throw new AppException(ErrorCode.CONFIRM_PASSWORD_INVALID);

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));

        return ChangePasswordResponse
                .builder()
                .message("Change password successful")
                .success(true)
                .build();
    }
}
