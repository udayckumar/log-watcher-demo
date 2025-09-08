package com.example.logwatcher.config;

import com.example.logwatcher.core.FileTailService;
import com.example.logwatcher.core.LogNotifier;
import com.example.logwatcher.web.LogWebSocketHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * Registers WebSocket endpoint and wires the handler as a Spring bean.
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final LogNotifier notifier;
    private final FileTailService tailService;

    @Value("${app.initial-lines:10}")
    private int initialLines;

    public WebSocketConfig(LogNotifier notifier, FileTailService tailService) {
        this.notifier = notifier;
        this.tailService = tailService;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(logHandler(), "/log-socket").setAllowedOrigins("*");
    }

    @Bean
    public LogWebSocketHandler logHandler() {
        return new LogWebSocketHandler(notifier, tailService, initialLines);
    }
}
