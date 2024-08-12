package com.emojinious.emojinious_backend.util;

import com.emojinious.emojinious_backend.constant.GamePhase;
import com.emojinious.emojinious_backend.dto.*;
import com.emojinious.emojinious_backend.model.GameSession;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class MessageUtil {
    private final SimpMessagingTemplate messagingTemplate;

    public void broadcastGameState(String sessionId, GameStateDto gameStateDto) {
        messagingTemplate.convertAndSend("/topic/game/" + sessionId, gameStateDto);
    }

    public void broadcastChatMessage(String sessionId, ChatMessage message) {
        messagingTemplate.convertAndSend("/topic/game/" + sessionId + "/chat", message);
    }

    public void sendToPlayer(String sessionId, String playerId, String type, Object data) {
        PlayerMessage message = new PlayerMessage(type, data);
        messagingTemplate.convertAndSendToUser(playerId, "/queue/game/" + sessionId, message, createHeaders(playerId));
    }

    public void updateSubmissionProgress(String sessionId, String type, int submitted, int total) {
        messagingTemplate.convertAndSend("/topic/game/" + sessionId + "/progress",
                Map.of("type", type, "submitted", submitted, "total", total));
    }

    public void broadcastPhaseStartMessage(String sessionId, GamePhase phase, String message) {
        messagingTemplate.convertAndSend("/topic/game/" + sessionId + "/phase",
                Map.of("phase", phase, "message", message));
    }

    public void broadcastGameResult(String sessionId, Map<String, Integer> scores) {
        messagingTemplate.convertAndSend("/topic/game/" + sessionId + "/result", scores);
    }

    private MessageHeaders createHeaders(String playerId) {
        SimpMessageHeaderAccessor headerAccessor = SimpMessageHeaderAccessor
                .create(SimpMessageType.MESSAGE);
        headerAccessor.setSessionId(playerId);
        headerAccessor.setLeaveMutable(true);
        return headerAccessor.getMessageHeaders();
    }
}