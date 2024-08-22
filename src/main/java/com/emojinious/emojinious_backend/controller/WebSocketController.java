package com.emojinious.emojinious_backend.controller;

import com.emojinious.emojinious_backend.cache.Player;
import com.emojinious.emojinious_backend.dto.*;
import com.emojinious.emojinious_backend.model.GameSession;
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
        System.out.println("WebSocketController.handleConnect");
        String playerId = message.getPlayerId();
        String token = message.getToken();

        // 토큰 검증 로직
        try {
            Claims claims = jwtUtil.validateToken(token);
            if (claims.getSubject().equals(playerId)) {
                Player player = playerService.getPlayerById(playerId);

                if (player != null) {
                    // 기존 연결 확인 및 처리
                    gameService.handleExistingConnection(player.getSessionId(), playerId);

                    player.setSocketId(headerAccessor.getSessionId());
                    playerService.savePlayer(player);
                    headerAccessor.getSessionAttributes().put("player", player);
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

    // TODO: 모든 요청에 대해 페이즈 검사 후 수락/거절
    @MessageMapping("/game/{sessionId}/join")
    @SendTo("/topic/game/{sessionId}")
    public GameStateDto joinGame(@DestinationVariable String sessionId,
                                 SimpMessageHeaderAccessor headerAccessor) {
        System.out.println("WebSocketController.joinGame");
        String playerId = (String) headerAccessor.getSessionAttributes().get("playerId");
        String nickname = (String) headerAccessor.getSessionAttributes().get("nickname");

        // 중복 참여 체크 및 처리
        if (gameService.isPlayerAlreadyJoined(sessionId, playerId)) {
            return gameService.getGameState(sessionId);
        }

        return gameService.joinGame(sessionId, playerId, nickname);
    }

    @MessageMapping("/game/{sessionId}/start")
    @SendTo("/topic/game/{sessionId}")
    public GameStateDto startGame(@DestinationVariable String sessionId,
                                  SimpMessageHeaderAccessor headerAccessor) {
        String playerId = (String) headerAccessor.getSessionAttributes().get("playerId");
        return gameService.startGame(sessionId, playerId);
    }

//    @MessageMapping("/game/{sessionId}/getKeywords")
//    @SendTo("/topic/game/{sessionId}")
//    public GameSession requestKeywords(@DestinationVariable String sessionId) {
//        GameSession gameSession = gameService.getGameSession(sessionId);
//        gameService.generateKeywords(gameSession);
//        return gameSession;
//    }

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
    public void sendChatMessage(@DestinationVariable String sessionId,
                                @Payload ChatMessage message,
                                SimpMessageHeaderAccessor headerAccessor) {
        String playerId = (String) headerAccessor.getSessionAttributes().get("playerId");
        String nickname = (String) headerAccessor.getSessionAttributes().get("nickname");
        message.setPlayerId(playerId);
        message.setSender(nickname);
        gameService.broadcastChatMessage(sessionId, message);
    }
}