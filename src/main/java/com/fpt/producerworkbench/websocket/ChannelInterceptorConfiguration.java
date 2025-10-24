package com.fpt.producerworkbench.websocket;

import com.fpt.producerworkbench.configuration.JwtDecoderCustomizer;
import io.micrometer.common.util.StringUtils;
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
@Slf4j
public class ChannelInterceptorConfiguration implements ChannelInterceptor {

    private final JwtDecoderCustomizer jwtDecoderCustomizer;

    public ChannelInterceptorConfiguration(JwtDecoderCustomizer jwtDecoderCustomizer) {
        this.jwtDecoderCustomizer = jwtDecoderCustomizer;
    }

    @Override
    public Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if(accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            log.info("Connected to channel");
            String authorizationHeader = accessor.getFirstNativeHeader("Authorization");
            if(StringUtils.isNotBlank(authorizationHeader)) {
                String token = authorizationHeader.replace("Bearer ", "");
                Jwt jwtDecoder = jwtDecoderCustomizer.decode(token);
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        jwtDecoder.getSubject(),
                        null,
                        List.of()
                );
                SecurityContextHolder.getContext().setAuthentication(authentication);
                accessor.setUser(authentication);
            }
        }

        return message;
    }

}
