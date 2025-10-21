package com.fpt.producerworkbench.configuration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

@Configuration
@EnableWebSocketMessageBroker
@Slf4j
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Enable simple broker for pub-sub pattern
        config.enableSimpleBroker("/topic", "/queue");

        // Set application destination prefix
        config.setApplicationDestinationPrefixes("/app");

        // Set user destination prefix for private messages
        config.setUserDestinationPrefix("/user");

        log.info("✅ Message broker configured: /topic, /queue, /app, /user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*") // For development, use specific origins in production
                .withSockJS()
                .setStreamBytesLimit(512 * 1024) // 512KB
                .setHttpMessageCacheSize(1000)
                .setDisconnectDelay(30 * 1000); // 30 seconds

        log.info("✅ WebSocket endpoint registered: /ws");
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        // Set message size limits
        registration
                .setMessageSizeLimit(128 * 1024)    // 128KB max message size
                .setSendBufferSizeLimit(512 * 1024) // 512KB send buffer
                .setSendTimeLimit(20 * 1000);       // 20 seconds timeout
        log.info("✅ WebSocket transport configured");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        // Configure thread pool for handling incoming messages
        registration.taskExecutor()
                .corePoolSize(4)
                .maxPoolSize(8)
                .keepAliveSeconds(60);

        log.info("✅ Client inbound channel configured");
    }

    @Override
    public void configureClientOutboundChannel(ChannelRegistration registration) {
        // Configure thread pool for sending messages to clients
        registration.taskExecutor()
                .corePoolSize(4)
                .maxPoolSize(8)
                .keepAliveSeconds(60);

        log.info("✅ Client outbound channel configured");
    }
}
