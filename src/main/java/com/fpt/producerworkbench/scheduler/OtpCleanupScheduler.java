package com.fpt.producerworkbench.scheduler;

import com.fpt.producerworkbench.entity.User;
import com.fpt.producerworkbench.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class OtpCleanupScheduler {

    private final UserRepository userRepository;

    @Scheduled(fixedRate = 1800000) // Chạy mỗi 30 phút
    public void cleanupExpiredOtp() {
        log.info("Start Scheduler Delete Otp Expired");
        List<User> usersWithExpiredOtp = userRepository.findAll().stream()
                .filter(user -> user.getOtp() != null && user.getOtpExpiryDate() != null)
                .filter(user -> user.getOtpExpiryDate().isBefore(LocalDateTime.now()))
                .toList();

        if (!usersWithExpiredOtp.isEmpty()) {
            usersWithExpiredOtp.forEach(user -> {
                user.setOtp(null);
                user.setOtpExpiryDate(null);
            });
            userRepository.saveAll(usersWithExpiredOtp);
        }
    }
}
