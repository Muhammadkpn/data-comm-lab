package com.learn.datacomm.realtime.ws;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final TransferWebSocketHandler handler;

    public WebSocketConfig(TransferWebSocketHandler handler) {
        this.handler = handler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // Origin dibatasi: tanpa ini, halaman di situs mana pun bisa membuka WebSocket
        // ke server kita memakai cookie user (Cross-Site WebSocket Hijacking).
        registry.addHandler(handler, "/ws/transfers")
                .setAllowedOrigins("http://localhost:8083");
    }
}
