package com.fpt.producerworkbench.configuration;

import com.fpt.producerworkbench.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class WebSocketAuthChannelInterceptor implements ChannelInterceptor {

    private final UserRepository userRepository;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            String userIdHeader = accessor.getFirstNativeHeader("userId");
            String liveSessionIdHeader = accessor.getFirstNativeHeader("liveSessionId");

            // ✅ Validate headers
            if (userIdHeader == null || liveSessionIdHeader == null) {
                log.warn("⚠️ Missing required headers: userId={}, liveSessionId={}",
                        userIdHeader, liveSessionIdHeader);
                return message;
            }

            try {
                Long userId = Long.parseLong(userIdHeader);
                accessor.getSessionAttributes().put("userId", userId);
                accessor.getSessionAttributes().put("liveSessionId", liveSessionIdHeader);

                log.info("🔌 STOMP CONNECT - userId: {}, liveSessionId: {}",
                        userId, liveSessionIdHeader);
            } catch (NumberFormatException e) {
                log.error("❌ Invalid userId format: {}", userIdHeader);
            }
        }

        return message;
    }

}
