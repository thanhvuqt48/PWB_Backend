package com.fpt.producerworkbench.websocket.listener;

import com.fpt.producerworkbench.entity.WebSocketSession;
import com.fpt.producerworkbench.service.impl.WebSocketSessionRedisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;

@Component
@RequiredArgsConstructor
@Slf4j
public class WebSocketEventListener {

    private final WebSocketSessionRedisService sessionRedisService;

    @Async
    @EventListener
    public void handleSessionConnect(SessionConnectEvent connectEvent) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(connectEvent.getMessage());

        Principal user = connectEvent.getUser();
        if(user == null) {
            throw new RuntimeException("Unauthenticated");
        }

        sessionRedisService.saveWebSocketSession(WebSocketSession.builder()
                        .socketSessionId(accessor.getSessionId())
                        .userId(user.getName())
                .build());

        log.info("Connected to websocket session {}", accessor.getSessionId());
    }

    @Async
    @EventListener
    public void handleSessionDisConnect(SessionDisconnectEvent disconnectEvent){
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(disconnectEvent.getMessage());
        sessionRedisService.deleteWebsocketSession(accessor.getSessionId());
        log.info("Disconnected from websocket session {}", accessor.getSessionId());
    }

}
