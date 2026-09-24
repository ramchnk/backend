package com.globalisor.backend.websocket;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    @Autowired
    private ChatWebSocketHandler chatWebSocketHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(chatWebSocketHandler, "/api/ws/chat")
                .setAllowedOriginPatterns("*")
                .setAllowedOrigins("*");
    }

    @org.springframework.beans.factory.annotation.Value("${websocket.idle.timeout.ms:120000}")
    private long idleTimeoutMs;

    @Bean
    public ServletServerContainerFactoryBean createWebSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxTextMessageBufferSize(64 * 1024); // 64 KB max buffer per session
        container.setMaxBinaryMessageBufferSize(64 * 1024); // 64 KB
        container.setMaxSessionIdleTimeout(idleTimeoutMs); // 2 minutes (120s) idle timeout to reclaim memory rapidly
        container.setAsyncSendTimeout(5000L); // 5 seconds async send timeout to prevent blocking
        return container;
    }
}

