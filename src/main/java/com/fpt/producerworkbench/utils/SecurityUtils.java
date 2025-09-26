package com.fpt.producerworkbench.utils;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.Random;

@Service
public class SecurityUtils {

    private SecurityUtils() {}

    private static final Random RANDOM = new Random();

    public static Optional<String> getCurrentUserLogin() {
        SecurityContext contextHolder = SecurityContextHolder.getContext();
        return Optional.ofNullable(extractPrincipal(contextHolder.getAuthentication()));
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
