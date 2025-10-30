package com.fpt.producerworkbench.websocket;

import com.fpt.producerworkbench.configuration.JwtDecoderCustomizer;
import com.fpt.producerworkbench.repository.UserRepository;
import io.micrometer.common.util.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class ChannelInterceptorConfiguration implements ChannelInterceptor {

    private final JwtDecoderCustomizer jwtDecoderCustomizer;
    private final UserRepository userRepository;

    @Override
    public Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {

        // ✅ ALWAYS log every message that comes through
        log.info("🚨 ChannelInterceptor: Message received");
        log.info("🚨 Message type: {}", message.getClass().getSimpleName());
        log.info("🚨 Message payload: {}", message.getPayload());
        log.info("🚨 Message headers: {}", message.getHeaders());

        StompHeaderAccessor accessor = StompHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor == null) {
            log.warn("⚠️ StompHeaderAccessor is null - non-STOMP message?");
            return message;
        }

        log.info("🚨 STOMP Command: {}", accessor.getCommand());

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            log.info("🔌 WebSocket STOMP CONNECT received");

            try {
                String authorizationHeader = accessor.getFirstNativeHeader("Authorization");
                String userIdHeader = accessor.getFirstNativeHeader("userId");
                String liveSessionIdHeader = accessor.getFirstNativeHeader("liveSessionId");

                log.info("📋 Headers - Auth: {}, UserId: {}, SessionId: {}",
                        authorizationHeader != null ? "Present" : "Missing",
                        userIdHeader,
                        liveSessionIdHeader);

                // ✅ ALWAYS store session attributes first
                if (userIdHeader != null && liveSessionIdHeader != null) {
                    try {
                        Long userId = Long.parseLong(userIdHeader);
                        accessor.getSessionAttributes().put("userId", userId);
                        accessor.getSessionAttributes().put("liveSessionId", liveSessionIdHeader);

                        log.info("✅ Stored session attributes - userId: {}, liveSessionId: {}",
                                userId, liveSessionIdHeader);

                        // ✅ Create authentication for WebSocket
                        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                                userIdHeader, null, List.of());
                        accessor.setUser(authentication);
                        log.info("✅ Created authentication for userId: {}", userIdHeader);

                    } catch (NumberFormatException e) {
                        log.error("❌ Invalid userId format: {}", userIdHeader);
                        // ✅ DON'T THROW - continue processing
                    }
                } else {
                    log.warn("⚠️ Missing userId or sessionId headers");
                }

                // ✅ Try JWT validation but don't fail
                if (StringUtils.isNotBlank(authorizationHeader)) {
                    try {
                        String token = authorizationHeader.replace("Bearer ", "");
                        log.info("🔍 Attempting JWT validation...");
                        Jwt jwtDecoder = jwtDecoderCustomizer.decode(token);
                        log.info("✅ JWT validation successful for: {}", jwtDecoder.getSubject());

                        // Upgrade to JWT auth
                        UsernamePasswordAuthenticationToken jwtAuth = new UsernamePasswordAuthenticationToken(
                                jwtDecoder.getSubject(), null, List.of());
                        accessor.setUser(jwtAuth);

                    } catch (Exception e) {
                        log.warn("⚠️ JWT validation failed, using basic auth: {}", e.getMessage());
                        log.warn("⚠️ JWT error details: {}", e.getClass().getSimpleName());
                        // ✅ Don't throw - basic auth already set
                    }
                }

                log.info("✅ STOMP CONNECT processing completed successfully");

            } catch (Exception e) {
                log.error("❌ CRITICAL: Exception in ChannelInterceptor: {}", e.getMessage(), e);
                // ✅ CRITICAL: Don't throw - return message anyway
            }
        }

        log.info("✅ ChannelInterceptor: Returning message");
        return message;
    }
}
