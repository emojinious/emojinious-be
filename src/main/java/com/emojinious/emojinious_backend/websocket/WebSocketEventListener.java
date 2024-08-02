package com.emojinious.emojinious_backend.websocket;

import com.emojinious.emojinious_backend.service.GameService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketEventListener {
    private final GameService gameService;

    @EventListener
    public void handleWebSocketConnectListener(SessionConnectedEvent event) {
        SimpMessageHeaderAccessor headerAccessor = SimpMessageHeaderAccessor.wrap(event.getMessage());
        Map<String, Object> sessionAttributes = headerAccessor.getSessionAttributes();

        if (sessionAttributes != null) {
            String sessionId = (String) sessionAttributes.get("sessionId");
            String playerId = (String) sessionAttributes.get("playerId");

            if (sessionId != null && playerId != null) {
                gameService.handlePlayerConnect(sessionId, playerId);
                log.info("Player connected - sessionId: {}, playerId: {}", sessionId, playerId);
            } else {
                log.warn("Session attributes are incomplete - sessionId: {}, playerId: {}", sessionId, playerId);
            }
        } else {
            log.warn("Session attributes are null for the connected event");
        }
    }

    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        SimpMessageHeaderAccessor headerAccessor = SimpMessageHeaderAccessor.wrap(event.getMessage());
        Map<String, Object> sessionAttributes = headerAccessor.getSessionAttributes();

        if (sessionAttributes != null) {
            String sessionId = (String) sessionAttributes.get("sessionId");
            String playerId = (String) sessionAttributes.get("playerId");

            if (sessionId != null && playerId != null) {
                gameService.handlePlayerDisconnect(sessionId, playerId);
                log.info("Player disconnected - sessionId: {}, playerId: {}", sessionId, playerId);
            } else {
                log.warn("Session attributes are incomplete for disconnect - sessionId: {}, playerId: {}", sessionId, playerId);
            }
        } else {
            log.warn("Session attributes are null for the disconnect event");
        }
    }
}