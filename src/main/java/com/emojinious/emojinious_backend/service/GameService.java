package com.emojinious.emojinious_backend.service;

import com.emojinious.emojinious_backend.cache.Player;
import com.emojinious.emojinious_backend.dto.*;
import com.emojinious.emojinious_backend.model.*;
import com.emojinious.emojinious_backend.constant.GamePhase;
import com.emojinious.emojinious_backend.constant.GameState;
import com.emojinious.emojinious_backend.util.JwtUtil;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GameService {
    private final RedisTemplate<String, Object> redisTemplate;
    private final SimpMessagingTemplate messagingTemplate;
    private final PlayerService playerService;
    private final JwtUtil jwtUtil;
    private final RandomWordGenerator randomWordGenerator;
    private final ImageGenerator imageGenerator;
    private final ScoreCalculator scoreCalculator;

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

    public GameStateDto startGame(String sessionId, String playerId) {
        GameSession gameSession = getGameSession(sessionId);
        if (!gameSession.isHost(playerId)) {
            throw new IllegalStateException("Only host can start the game");
        }
        gameSession.startGame();
        startDescriptionPhase(gameSession);
        updateGameSession(gameSession);
        return createGameStateDto(gameSession);
    }

    public GameStateDto submitPrompt(String sessionId, String playerId, String prompt) {
        GameSession gameSession = getGameSession(sessionId);
        gameSession.submitPrompt(playerId, prompt);
        if (gameSession.getCurrentPrompts().size() == gameSession.getPlayers().size()) {
            startGenerationPhase(gameSession);
        }
        updateGameSession(gameSession);
        broadcastGameState(gameSession);
        return createGameStateDto(gameSession);
    }

    public GameStateDto submitGuess(String sessionId, String playerId, String guess) {
        GameSession gameSession = getGameSession(sessionId);
        gameSession.submitGuess(playerId, guess);
        if (gameSession.getCurrentGuesses().size() == gameSession.getPlayers().size()) {
            moveToNextPhase(gameSession);
        }
        updateGameSession(gameSession);
        broadcastGameState(gameSession);
        return createGameStateDto(gameSession);
    }

    private void startDescriptionPhase(GameSession gameSession) {
        gameSession.setCurrentPhase(GamePhase.DESCRIPTION);
        Map<String, String> keywords = randomWordGenerator.generateKeywords(gameSession.getPlayers().size());
        gameSession.setCurrentKeywords(keywords);
        gameSession.getPlayers().forEach(player ->
                sendToPlayer(gameSession.getSessionId(), player.getId(), "keyword", keywords.get(player.getId())));
        broadcastGameState(gameSession);
    }

    private void startGenerationPhase(GameSession gameSession) {
        gameSession.setCurrentPhase(GamePhase.GENERATION);
        gameSession.getCurrentPrompts().forEach((playerId, prompt) -> {
            String imageUrl = imageGenerator.generateImage(prompt);
            gameSession.setGeneratedImage(playerId, imageUrl);
        });
        if (gameSession.areAllImagesGenerated()) {
            startGuessingPhase(gameSession);
        }
        broadcastGameState(gameSession);
    }

    private void startGuessingPhase(GameSession gameSession) {
        gameSession.setCurrentPhase(GamePhase.GUESSING);
        gameSession.getPlayers().forEach(player -> {
            String imageUrl = getNextImageForPlayer(gameSession, player.getId());
            sendToPlayer(gameSession.getSessionId(), player.getId(), "image", imageUrl);
        });
        broadcastGameState(gameSession);
    }

    private void moveToNextPhase(GameSession gameSession) {
        gameSession.moveToNextPhase();
        switch (gameSession.getCurrentPhase()) {
            case DESCRIPTION:
                startDescriptionPhase(gameSession);
                break;
            case GENERATION:
                startGenerationPhase(gameSession);
                break;
            case GUESSING:
                startGuessingPhase(gameSession);
                break;
            case RESULT:
                endGame(gameSession);
                break;
        }
    }

    private void endGame(GameSession gameSession) {
        gameSession.setState(GameState.FINISHED);
        Map<String, Integer> scores = scoreCalculator.calculateScores(gameSession);
        gameSession.getPlayers().forEach(player ->
                player.setScore(scores.get(player.getId())));
        broadcastGameState(gameSession);
        redisTemplate.delete("game:session:" + gameSession.getSessionId());
    }

    @Scheduled(fixedRate = 1000)
    public void checkPhaseTimeouts() {
        // TODO: 페이즈 타임아웃 구현.ㅇ
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

    public void broadcastChatMessage(String sessionId, ChatMessage message) {
        messagingTemplate.convertAndSend("/topic/game/" + sessionId + "/chat", message);
    }

    private void sendToPlayer(String sessionId, String playerId, String type, Object data) {
        PlayerMessage message = new PlayerMessage(type, data);
        messagingTemplate.convertAndSendToUser(playerId, "/queue/game/" + sessionId, message);
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
        dto.setCurrentPhase(gameSession.getCurrentPhase().ordinal());
        dto.setRemainingTime(gameSession.getRemainingTime());

        if (gameSession.getCurrentPhase() == GamePhase.DESCRIPTION) {
            dto.setCurrentPrompts(gameSession.getCurrentPrompts());
        } else if (gameSession.getCurrentPhase() == GamePhase.GUESSING) {
            dto.setCurrentGuesses(gameSession.getCurrentGuesses());
        }

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

    private String getNextImageForPlayer(GameSession gameSession, String playerId) {
        List<String> playerIds = gameSession.getPlayers().stream()
                .map(Player::getId)
                .collect(Collectors.toList());
        int currentIndex = playerIds.indexOf(playerId);
        int nextIndex = (currentIndex + 1) % playerIds.size();
        String nextPlayerId = playerIds.get(nextIndex);
        return gameSession.getGeneratedImages().get(nextPlayerId);
    }

    public void refreshPlayerToken(String sessionId, String playerId) {
        GameSession gameSession = getGameSession(sessionId);
        Player player = gameSession.getPlayerById(playerId);
        if (player != null) {
            String newToken = jwtUtil.refreshToken(player.getToken());
            player.setToken(newToken);
            updateGameSession(gameSession);
            sendToPlayer(sessionId, playerId, "token", newToken);
        }
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
            endGame(gameSession);
        } else {
            updateGameSession(gameSession);
            broadcastGameState(gameSession);
        }
    }

    public GameStateDto getGameState(String sessionId) {
        GameSession gameSession = getGameSession(sessionId);
        return createGameStateDto(gameSession);
    }
}