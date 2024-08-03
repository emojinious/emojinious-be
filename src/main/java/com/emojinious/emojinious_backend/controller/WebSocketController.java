package com.emojinious.emojinious_backend.controller;

import com.emojinious.emojinious_backend.dto.*;
import com.emojinious.emojinious_backend.cache.Player;
import com.emojinious.emojinious_backend.service.GameService;
import com.emojinious.emojinious_backend.service.PlayerService;
import com.emojinious.emojinious_backend.util.JwtUtil;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class WebSocketController {

    private final GameService gameService;
    private final PlayerService playerService;
    private final JwtUtil jwtUtil;

    @MessageMapping("/connect")
    @SendToUser("/queue/connect-ack")
    public String handleConnect(SimpMessageHeaderAccessor headerAccessor,
                                @Payload ConnectMessage message) {
        String playerId = message.getPlayerId();
        String token = message.getToken();

        // 토큰 검증 로직
        try {
            Claims claims = jwtUtil.validateToken(token);
            if (claims.getSubject().equals(playerId)) {
                Player player = playerService.getPlayerById(playerId);

                if (player != null) {
                    headerAccessor.getSessionAttributes().put("playerId", playerId);
                    headerAccessor.getSessionAttributes().put("sessionId", claims.get("sessionId", String.class));
                    headerAccessor.getSessionAttributes().put("nickname", player.getNickname());
                    headerAccessor.getSessionAttributes().put("characterId", player.getCharacterId());
                    headerAccessor.getSessionAttributes().put("isHost", player.isHost());

                    return "Connected successfully";
                } else {
                    return "Player not found";
                }
            }
        } catch (Exception e) {
            return "Invalid token";
        }
        return "Connection failed";
    }

    @MessageMapping("/game/{sessionId}/join")
    @SendTo("/topic/game/{sessionId}")
    public GameStateDto joinGame(@DestinationVariable String sessionId,
                                 SimpMessageHeaderAccessor headerAccessor) {
        String playerId = (String) headerAccessor.getSessionAttributes().get("playerId");
        String nickname = (String) headerAccessor.getSessionAttributes().get("nickname");
        return gameService.joinGame(sessionId, playerId, nickname);
    }

    @MessageMapping("/game/{sessionId}/start")
    @SendTo("/topic/game/{sessionId}")
    public GameStateDto startGame(@DestinationVariable String sessionId,
                                  SimpMessageHeaderAccessor headerAccessor) {
        String playerId = (String) headerAccessor.getSessionAttributes().get("playerId");
        return gameService.startGame(sessionId, playerId);
    }

    @MessageMapping("/game/{sessionId}/prompt")
    @SendTo("/topic/game/{sessionId}")
    public GameStateDto submitPrompt(@DestinationVariable String sessionId,
                                     @Payload PromptSubmissionMessage message,
                                     SimpMessageHeaderAccessor headerAccessor) {
        String playerId = (String) headerAccessor.getSessionAttributes().get("playerId");
        return gameService.submitPrompt(sessionId, playerId, message.getPrompt());
    }

    @MessageMapping("/game/{sessionId}/guess")
    @SendTo("/topic/game/{sessionId}")
    public GameStateDto submitGuess(@DestinationVariable String sessionId,
                                    @Payload GuessSubmissionMessage message,
                                    SimpMessageHeaderAccessor headerAccessor) {
        String playerId = (String) headerAccessor.getSessionAttributes().get("playerId");
        return gameService.submitGuess(sessionId, playerId, message.getGuess());
    }

    @MessageMapping("/game/{sessionId}/chat")
    @SendTo("/topic/game/{sessionId}/chat")
    public ChatMessage sendChatMessage(@DestinationVariable String sessionId,
                                       @Payload ChatMessage message,
                                       SimpMessageHeaderAccessor headerAccessor) {
        String playerId = (String) headerAccessor.getSessionAttributes().get("playerId");
        String nickname = (String) headerAccessor.getSessionAttributes().get("nickname");
        message.setPlayerId(playerId);
        message.setSender(nickname);
        return message;
    }
}