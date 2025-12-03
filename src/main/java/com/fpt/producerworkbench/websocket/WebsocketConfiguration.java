package com.fpt.producerworkbench.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

import java.util.List;

@Configuration
@RequiredArgsConstructor
@EnableWebSocketMessageBroker
@Slf4j
public class WebsocketConfiguration implements WebSocketMessageBrokerConfigurer {

    private final ChannelInterceptorConfiguration channelInterceptor;

    @Override
    public void registerStompEndpoints(@NonNull StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .addInterceptors(new WebSocketHandshakeInterceptor())
                .setAllowedOrigins("http://localhost:5173", "https://www.producerworkbench.io.vn")
                .setAllowedOriginPatterns("*")
                .withSockJS()
                .setStreamBytesLimit(512 * 1024)
                .setHttpMessageCacheSize(1000)
                .setDisconnectDelay(30 * 1000);
        log.info("✅ WebSocket endpoint registered: /ws");
    }

    @Override
    public void configureMessageBroker(@NonNull MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
        log.info("✅ Message broker configured: /topic, /queue, /app, /user");

    }

    @Override
    public void configureClientInboundChannel(@NonNull ChannelRegistration registration) {
        registration.interceptors(channelInterceptor);
        registration.taskExecutor()
                .corePoolSize(12)
                .maxPoolSize(18)
                .keepAliveSeconds(60)
                .queueCapacity(800);
        log.info("✅ Client inbound channel configured with auth interceptor");
    }

    @Override
    public void configureClientOutboundChannel(@NonNull ChannelRegistration registration) {
        registration.taskExecutor()
                .corePoolSize(6)
                .maxPoolSize(12)
                .queueCapacity(1000)
                .keepAliveSeconds(60);
        log.info("✅ Client outbound channel configured");
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration
                .setMessageSizeLimit(128 * 1024)
                .setSendBufferSizeLimit(512 * 1024)
                .setSendTimeLimit(20 * 1000);
        log.info("✅ WebSocket transport configured");
    }

}
