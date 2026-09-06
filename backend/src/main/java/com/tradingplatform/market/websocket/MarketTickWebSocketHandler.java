package com.tradingplatform.market.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/** Browser-facing half of the market stream. It never connects to Upstox. */
@Component
public class MarketTickWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(MarketTickWebSocketHandler.class);
    private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();
    private final ObjectMapper json;

    public MarketTickWebSocketHandler(ObjectMapper json) {
        this.json = json;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);
        log.info("Market WebSocket connected: {}", session.getId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
        log.info("Market WebSocket disconnected: {} ({})", session.getId(), status);
    }

    public void publish(MarketTick tick) {
        if (sessions.isEmpty()) {
            return;
        }
        try {
            TextMessage message = new TextMessage(json.writeValueAsString(tick));
            for (WebSocketSession session : sessions) {
                if (!session.isOpen()) {
                    sessions.remove(session);
                    continue;
                }
                try {
                    synchronized (session) {
                        session.sendMessage(message);
                    }
                } catch (IOException e) {
                    sessions.remove(session);
                    log.debug("Unable to send market tick to {}", session.getId(), e);
                }
            }
        } catch (Exception e) {
            log.warn("Unable to serialize market tick", e);
        }
    }
}
