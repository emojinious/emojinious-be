package com.emojinious.emojinious_backend.service;

import com.emojinious.emojinious_backend.dto.*;
import com.emojinious.emojinious_backend.model.*;
import com.emojinious.emojinious_backend.constant.GameState;
import com.emojinious.emojinious_backend.util.JwtUtil;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class GameService {
    private final RedisTemplate<String, Object> redisTemplate;
    private final SimpMessagingTemplate messagingTemplate;
    private final PlayerService playerService;
    private final JwtUtil jwtUtil;

    public GameStateDto joinGame(String sessionId, String playerId, String nickname) {
        GameSession gameSession = getOrCreateGameSession(sessionId);
        Player player = playerService.getPlayerById(playerId);
        if (player == null) {
            player = new Player(playerId, nickname, 0, gameSession.getPlayers().isEmpty());
            playerService.savePlayer(player);
        }
        gameSession.addPlayer(player);
        updateGameSession(gameSession);
        broadcastGameState(gameSession);
        return createGameStateDto(gameSession);
    }

    public void handlePlayerConnect(String sessionId, String playerId) {
        GameSession gameSession = getGameSession(sessionId);
        Player player = playerService.getPlayerById(playerId);
        if (player != null && !gameSession.getPlayers().contains(player)) {
            gameSession.addPlayer(player);
            updateGameSession(gameSession);
            broadcastGameState(gameSession);
        }
    }

    public void handlePlayerDisconnect(String sessionId, String playerId) {
        GameSession gameSession = getGameSession(sessionId);
        gameSession.removePlayer(playerId);
        if (gameSession.getPlayers().isEmpty()) {
            endGame(sessionId);
        } else {
            updateGameSession(gameSession);
            broadcastGameState(gameSession);
        }
    }


    public GameStateDto startGame(String sessionId, String playerId) {
        GameSession gameSession = getGameSession(sessionId);
        if (!gameSession.isHost(playerId)) {
            throw new IllegalStateException("Only host can start the game");
        }
        gameSession.startGame();
        updateGameSession(gameSession);
        broadcastGameState(gameSession);
        scheduleNextTurn(sessionId);
        return createGameStateDto(gameSession);
    }

    public GameStateDto submitPrompt(String sessionId, String playerId, String prompt) {
        GameSession gameSession = getGameSession(sessionId);
        gameSession.submitPrompt(playerId, prompt);
        updateGameSession(gameSession);
        if (gameSession.getCurrentPrompts().size() == gameSession.getPlayers().size()) {
            gameSession.moveToGuessingPhase();
            scheduleNextTurn(sessionId);
        }
        broadcastGameState(gameSession);
        return createGameStateDto(gameSession);
    }

    public GameStateDto submitGuess(String sessionId, String playerId, String guess) {
        GameSession gameSession = getGameSession(sessionId);
        gameSession.submitGuess(playerId, guess);
        updateGameSession(gameSession);
        if (gameSession.getCurrentGuesses().size() == gameSession.getPlayers().size()) {
            gameSession.moveToNextTurn();
            if (gameSession.getState() == GameState.FINISHED) {
                endGame(sessionId);
            } else {
                scheduleNextTurn(sessionId);
            }
        }
        broadcastGameState(gameSession);
        return createGameStateDto(gameSession);
    }


    public void refreshPlayerToken(String sessionId, String playerId) {
        GameSession gameSession = getGameSession(sessionId);
        Player player = gameSession.getPlayerById(playerId);
        if (player != null) {
            String newToken = jwtUtil.refreshToken(player.getToken());
            player.setToken(newToken);
            updateGameSession(gameSession);
            messagingTemplate.convertAndSendToUser(playerId, "/queue/token", newToken);
        }
    }

    private GameSession getOrCreateGameSession(String sessionId) {
        GameSession gameSession = (GameSession) redisTemplate.opsForValue().get("game:session:" + sessionId);
        if (gameSession == null) {
            gameSession = new GameSession(sessionId);
        }
        return gameSession;
    }

    private GameSession getGameSession(String sessionId) {
        GameSession gameSession = (GameSession) redisTemplate.opsForValue().get("game:session:" + sessionId);
        if (gameSession == null) {
            throw new IllegalStateException("Game session not found");
        }
        return gameSession;
    }

    private void updateGameSession(GameSession gameSession) {
        redisTemplate.opsForValue().set("game:session:" + gameSession.getSessionId(), gameSession);
    }

    private void broadcastGameState(GameSession gameSession) {
        GameStateDto gameStateDto = createGameStateDto(gameSession);
        messagingTemplate.convertAndSend("/topic/game/" + gameSession.getSessionId(), gameStateDto);
    }

    private GameStateDto createGameStateDto(GameSession gameSession) {
        GameStateDto dto = new GameStateDto();
        dto.setSessionId(gameSession.getSessionId());
        dto.setPlayers(gameSession.getPlayers().stream()
                .map(this::convertToPlayerDto)
                .collect(Collectors.toList()));
        dto.setSettings(convertToGameSettingsDto(gameSession.getSettings()));
        dto.setState(gameSession.getState());
        dto.setCurrentTurn(gameSession.getCurrentTurn());
        dto.setCurrentPrompts(gameSession.getCurrentPrompts());
        dto.setCurrentGuesses(gameSession.getCurrentGuesses());
        dto.setRemainingTime(Math.max(0, gameSession.getTurnEndTime() - System.currentTimeMillis()));
        return dto;
    }

    private PlayerDto convertToPlayerDto(Player player) {
        PlayerDto dto = new PlayerDto();
        dto.setId(player.getId());
        dto.setNickname(player.getNickname());
        dto.setCharacterId(player.getCharacterId());
        dto.setHost(player.isHost());
        dto.setScore(player.getScore());
        return dto;
    }

    private GameSettingsDto convertToGameSettingsDto(GameSettings settings) {
        GameSettingsDto dto = new GameSettingsDto();
        dto.setPromptTimeLimit(settings.getPromptTimeLimit());
        dto.setGuessTimeLimit(settings.getGuessTimeLimit());
        dto.setDifficulty(settings.getDifficulty());
        dto.setTurns(settings.getTurns());
        return dto;
    }

    private void scheduleNextTurn(String sessionId) {
        GameSession gameSession = getGameSession(sessionId);
        long delay = gameSession.getTurnEndTime() - System.currentTimeMillis();
        redisTemplate.opsForValue().set("game:turn:" + sessionId, true, delay, TimeUnit.MILLISECONDS);
    }

    private void endGame(String sessionId) {
        GameSession gameSession = getGameSession(sessionId);
        gameSession.setState(GameState.FINISHED);
        updateGameSession(gameSession);
        broadcastGameState(gameSession);
        redisTemplate.delete("game:session:" + sessionId);
    }
}