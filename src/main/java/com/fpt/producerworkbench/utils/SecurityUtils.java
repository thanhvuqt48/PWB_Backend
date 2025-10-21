package com.fpt.producerworkbench.utils;

import com.fpt.producerworkbench.entity.User;
import com.fpt.producerworkbench.exception.AppException;
import com.fpt.producerworkbench.exception.ErrorCode;
import com.fpt.producerworkbench.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Random;

@Component
@RequiredArgsConstructor // ✅ Use Lombok constructor injection
public class SecurityUtils {

    private final UserRepository userRepository; // ✅ Non-static field
    private static final Random RANDOM = new Random();


    public Long getCurrentUserId() {
        SecurityContext contextHolder = SecurityContextHolder.getContext();
        Authentication authentication = contextHolder.getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        Object principal = authentication.getPrincipal();

        // JWT case (your project)
        if (principal instanceof Jwt jwt) {
            String email = jwt.getSubject();
            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
            return user.getId();
        }

        // User entity case
        if (principal instanceof User user) {
            return user.getId();
        }

        // UserDetails case
        if (principal instanceof UserDetails userDetails) {
            User user = userRepository.findByEmail(userDetails.getUsername())
                    .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
            return user.getId();
        }

        throw new AppException(ErrorCode.UNAUTHENTICATED);
    }

    // ✅ Static methods stay static (no repository needed)
    public static Optional<String> getCurrentUserLogin() {
        SecurityContext contextHolder = SecurityContextHolder.getContext();
        return Optional.ofNullable(extractPrincipal(contextHolder.getAuthentication()));
    }

    public static Optional<String> getCurrentUserEmail() {
        SecurityContext contextHolder = SecurityContextHolder.getContext();
        Authentication authentication = contextHolder.getAuthentication();

        if (authentication == null) {
            return Optional.empty();
        }

        Object principal = authentication.getPrincipal();

        if (principal instanceof Jwt jwt) {
            return Optional.ofNullable(jwt.getSubject());
        }

        if (principal instanceof User user) {
            return Optional.ofNullable(user.getEmail());
        }

        if (principal instanceof UserDetails userDetails) {
            return Optional.ofNullable(userDetails.getUsername());
        }

        return Optional.empty();
    }

    private static String extractPrincipal(Authentication authentication) {
        if (authentication == null) {
            return null;
        } else if (authentication.getPrincipal() instanceof UserDetails springSecurityUser) {
            return springSecurityUser.getUsername();
        } else if (authentication.getPrincipal() instanceof Jwt jwt) {
            return jwt.getSubject();
        } else if (authentication.getPrincipal() instanceof String s) {
            return s;
        } else if (authentication.getPrincipal() instanceof User user) {
            return user.getEmail();
        }
        return null;
    }

    public static String generateOtp() {
        StringBuilder otp = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            otp.append(RANDOM.nextInt(10));
        }
        return otp.toString();
    }
}
