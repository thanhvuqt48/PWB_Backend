package com.fpt.producerworkbench.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

@Configuration
@Slf4j
public class WebSocketHandshakeInterceptor implements HandshakeInterceptor {

    @Override
    public boolean beforeHandshake(@NonNull ServerHttpRequest request,
            @NonNull ServerHttpResponse response,
            @NonNull WebSocketHandler wsHandler,
            @NonNull Map<String, Object> attributes) throws Exception {

        log.info("🤝 WebSocket handshake request from: {}", request.getRemoteAddress());
        log.info("🤝 Request URI: {}", request.getURI());
        log.info("🤝 Request headers: {}", request.getHeaders());

        // extract token, save userId to redis, save userId to session of websocket
        return true;
    }

    @Override
    public void afterHandshake(@NonNull ServerHttpRequest request,
            @NonNull ServerHttpResponse response,
            @NonNull WebSocketHandler wsHandler,
            @Nullable Exception exception) {
        if (exception != null) {
            log.error("❌ WebSocket handshake failed: {}", exception.getMessage(), exception);
        } else {
            log.info("✅ WebSocket handshake successful");
        }
        // delete userId from redis
    }

}
