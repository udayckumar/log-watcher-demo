package com.example.logwatcher;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class LogWebSocketHandler extends TextWebSocketHandler {
    private static final Logger logger = LoggerFactory.getLogger(LogWebSocketHandler.class);

    private final Set<WebSocketSession> sessionSet = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private final  fileTailService;
}
