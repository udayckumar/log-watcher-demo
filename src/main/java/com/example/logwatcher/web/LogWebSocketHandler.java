package com.example.logwatcher.web;

import com.example.logwatcher.core.FileTailService;
import com.example.logwatcher.core.LogNotifier;
import com.example.logwatcher.core.LogObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * WebSocket handler that registers itself as a LogObserver and sends each incoming line to client(s).
 * On new connection, it sends the last N lines using the tail service's strategy.
 * <p>
 * Note: For production you'd add authentication, origin checks, and possibly message throttling.
 */
public class LogWebSocketHandler extends TextWebSocketHandler implements LogObserver {

    private static final Logger logger = LoggerFactory.getLogger(LogWebSocketHandler.class);

    private final LogNotifier notifier;
    private final FileTailService tailService;
    private final int initialLines;
    private final AtomicBoolean registered = new AtomicBoolean(false);
    // simple design: a handler instance per WebSocket mapping in Spring is a singleton by default,
    // so we broadcast by storing session in a thread-safe structure. For simplicity we'll register one handler instance
    // with the notifier and use the WebSocketSession set to send messages. If multiple sessions needed, manage them via a set.
    private volatile WebSocketSession session;

    public LogWebSocketHandler(LogNotifier notifier, FileTailService tailService, int initialLines) {
        this.notifier = notifier;
        this.tailService = tailService;
        this.initialLines = initialLines;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        logger.info("WebSocket connected: {}", session.getId());
        this.session = session;
        // register as observer once
        if (registered.compareAndSet(false, true)) {
            notifier.addObserver(this);
        }
        // send initial last-N lines
        try {
            String[] last = tailService.readLastNLines(initialLines);
            for (String line : last) {
                if (line != null) sendToSession(line);
            }
        } catch (Exception e) {
            logger.warn("Error reading last lines for new client: {}", e.getMessage());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        logger.info("WebSocket closed: {} status {}", session.getId(), status);
        // do not remove observer globally (other sessions could still exist), for production track per-session observers.
        this.session = null;
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        logger.warn("Transport error for {}: {}", session.getId(), exception.getMessage());
    }

    /**
     * Implementation of LogObserver - called by notifier on every new line.
     */
    @Override
    public void onNewLogLine(String line) {
        sendToSession(line);
    }

    private void sendToSession(String line) {
        try {
            WebSocketSession s = this.session;
            if (s != null && s.isOpen()) {
                s.sendMessage(new TextMessage(line));
            }
        } catch (Exception e) {
            logger.warn("Failed to send to websocket session: {}", e.getMessage());
        }
    }
}
