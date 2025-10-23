// java
package com.fpt.producerworkbench.configuration;

import com.fpt.producerworkbench.entity.User;
import com.fpt.producerworkbench.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.Map;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class WebSocketAuthChannelInterceptor implements ChannelInterceptor {

    private final UserRepository userRepository;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
            if (sessionAttributes == null) {
                return message;
            }

            // 1) Put user info into session attributes when available
            Principal principal = accessor.getUser();
            if (principal != null) {
                String email = principal.getName();
                try {
                    Optional<User> maybeUser = userRepository.findByEmail(email);
                    maybeUser.ifPresent(user -> {
                        sessionAttributes.put("userId", user.getId());
                        sessionAttributes.put("userEmail", email);
                        log.debug("Set ws session attributes userId={} userEmail={}", user.getId(), email);
                    });
                } catch (Exception e) {
                    log.debug("Failed to lookup user for ws connect: {}", e.getMessage());
                }
            }

            // 2) Accept liveSessionId from client CONNECT native header and store it
            String liveSessionId = accessor.getFirstNativeHeader("liveSessionId");
            if (liveSessionId != null && !liveSessionId.isBlank()) {
                sessionAttributes.put("liveSessionId", liveSessionId);
                log.debug("Set ws session attribute liveSessionId={}", liveSessionId);
            }
        }

        return message;
    }
}
