package com.emojinious.emojinious_backend.service;

import com.emojinious.emojinious_backend.cache.Player;
import com.emojinious.emojinious_backend.dto.*;
import com.emojinious.emojinious_backend.model.*;
import com.emojinious.emojinious_backend.constant.GamePhase;
import com.emojinious.emojinious_backend.constant.GameState;
import com.emojinious.emojinious_backend.util.JwtUtil;
import com.emojinious.emojinious_backend.util.RedisUtil;
import com.emojinious.emojinious_backend.util.MessageUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GameService {
    private final RedisTemplate<String, String> redisTemplate;
    private final RedisUtil redisUtil;
    private final MessageUtil messageUtil;
    private final PlayerService playerService;
    private final JwtUtil jwtUtil;
    private final RandomWordGenerator randomWordGenerator;
    private final ImageGenerator imageGenerator;
    private final ScoreCalculator scoreCalculator;

    private static final String GAME_SESSION_KEY = "game:session:";

    public GameStateDto joinGame(String sessionId, String playerId, String nickname) {
        GameSession gameSession = getOrCreateGameSession(sessionId);
        Player player = playerService.getPlayerById(playerId);
        gameSession.addPlayer(player);
        updateGameSession(gameSession);
        messageUtil.broadcastGameState(gameSession.getSessionId(), createGameStateDto(gameSession));
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
        updateSubmissionProgress(gameSession, "prompt");
        if (gameSession.getCurrentPrompts().size() == gameSession.getPlayers().size()) {
            startGenerationPhase(gameSession);
        }
        updateGameSession(gameSession);
        messageUtil.broadcastGameState(gameSession.getSessionId(), createGameStateDto(gameSession));
        return createGameStateDto(gameSession);
    }

    public GameStateDto submitGuess(String sessionId, String playerId, String guess) {
        GameSession gameSession = getGameSession(sessionId);
        gameSession.submitGuess(playerId, guess);
        updateSubmissionProgress(gameSession, "guess");
        if (gameSession.getCurrentGuesses().size() == gameSession.getPlayers().size()) {
            moveToNextPhase(gameSession);
        }
        updateGameSession(gameSession);
        messageUtil.broadcastGameState(gameSession.getSessionId(), createGameStateDto(gameSession));
        return createGameStateDto(gameSession);
    }

    private void startDescriptionPhase(GameSession gameSession) {
        gameSession.setCurrentPhase(GamePhase.DESCRIPTION);
        messageUtil.broadcastPhaseStartMessage(gameSession.getSessionId(), gameSession.getCurrentPhase(), "KeyWord Generation");
        Map<String, String> keywords = randomWordGenerator.generateKeywords(gameSession.getPlayers());
        gameSession.setCurrentKeywords(keywords);
        gameSession.getPlayers().forEach(player ->
                messageUtil.sendToPlayer(gameSession.getSessionId(), player.getSocketId(), "keyword", keywords.get(player.getId())));
        messageUtil.broadcastGameState(gameSession.getSessionId(), createGameStateDto(gameSession));
        messageUtil.broadcastPhaseStartMessage(gameSession.getSessionId(), gameSession.getCurrentPhase(), "Description Phase");
        updateSubmissionProgress(gameSession, "prompt");
    }

    private void startGenerationPhase(GameSession gameSession) {
        gameSession.setCurrentPhase(GamePhase.GENERATION);
        messageUtil.broadcastPhaseStartMessage(gameSession.getSessionId(), gameSession.getCurrentPhase(), "Image Generation");
        messageUtil.broadcastGameState(gameSession.getSessionId(), createGameStateDto(gameSession));

        // TODO: 동시 요청, 이미지 생성 후 바로 다음 페이즈로 넘어가도록 수정
        gameSession.getCurrentPrompts().forEach((playerId, prompt) -> {
            String imageUrl = imageGenerator.generateImage(prompt);
            gameSession.setGeneratedImage(playerId, imageUrl);
        });

        if (gameSession.areAllImagesGenerated()) {
            startGuessingPhase(gameSession);
        }
    }

    private void startGuessingPhase(GameSession gameSession) {
        gameSession.setCurrentPhase(GamePhase.GUESSING);
        gameSession.getPlayers().forEach(player -> {
            String imageUrl = getNextImageForPlayer(gameSession, player.getId());
            messageUtil.sendToPlayer(gameSession.getSessionId(), player.getSocketId(), "image", imageUrl);
        });
        messageUtil.broadcastGameState(gameSession.getSessionId(), createGameStateDto(gameSession));
        messageUtil.broadcastPhaseStartMessage(gameSession.getSessionId(), gameSession.getCurrentPhase(), "추측 페이즈가 시작되었습니다. 이미지를 보고 키워드를 추측해주세요.");
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
        gameSession.setCurrentPhase(GamePhase.RESULT);
        Map<String, Integer> scores = scoreCalculator.calculateScores(gameSession);
        gameSession.getPlayers().forEach(player ->
                player.setScore(scores.get(player.getId())));
        messageUtil.broadcastGameState(gameSession.getSessionId(), createGameStateDto(gameSession));
        messageUtil.broadcastPhaseStartMessage(gameSession.getSessionId(), gameSession.getCurrentPhase(), "게임이 종료되었습니다. 결과를 확인해주세요.");
        messageUtil.broadcastGameResult(gameSession.getSessionId(), scores);
        redisTemplate.delete(GAME_SESSION_KEY + gameSession.getSessionId());
    }

    @Scheduled(fixedRate = 500)
    public void checkPhaseTimeouts() {
        List<String> sessionIds = redisTemplate.keys(GAME_SESSION_KEY + "*").stream()
                .map(key -> key.replace(GAME_SESSION_KEY, ""))
                .collect(Collectors.toList());

        for (String sessionId : sessionIds) {
            GameSession gameSession = getGameSession(sessionId);
            if (gameSession.getState() == GameState.IN_PROGRESS && gameSession.isPhaseTimedOut()) {
                moveToNextPhase(gameSession);
                updateGameSession(gameSession);
                messageUtil.broadcastGameState(gameSession.getSessionId(), createGameStateDto(gameSession));
            }
        }
    }

    private GameSession getOrCreateGameSession(String sessionId) {
        GameSession gameSession = redisUtil.get(GAME_SESSION_KEY + sessionId, GameSession.class);
        return gameSession != null ? gameSession : new GameSession(sessionId);
    }

    private GameSession getGameSession(String sessionId) {
        GameSession gameSession = redisUtil.get(GAME_SESSION_KEY + sessionId, GameSession.class);
        if (gameSession == null) {
            throw new IllegalStateException("Game session not found");
        }
        return gameSession;
    }

    private void updateGameSession(GameSession gameSession) {
        redisUtil.set(GAME_SESSION_KEY + gameSession.getSessionId(), gameSession);
    }

    public void broadcastChatMessage(String sessionId, ChatMessage message) {
        messageUtil.broadcastChatMessage(sessionId, message);
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
            messageUtil.sendToPlayer(sessionId, playerId, "token", newToken);
        }
    }

    public void handlePlayerConnect(String sessionId, String playerId) {
        GameSession gameSession = getGameSession(sessionId);
        Player player = playerService.getPlayerById(playerId);
        if (player != null && !gameSession.getPlayers().contains(player)) {
            gameSession.addPlayer(player);
            updateGameSession(gameSession);
            messageUtil.broadcastGameState(gameSession.getSessionId(), createGameStateDto(gameSession));
        }
    }

    public void handlePlayerDisconnect(String sessionId, String playerId) {
        GameSession gameSession = getGameSession(sessionId);
        gameSession.removePlayer(playerId);
        if (gameSession.getPlayers().isEmpty()) {
            endGame(gameSession);
        } else {
            updateGameSession(gameSession);
            messageUtil.broadcastGameState(gameSession.getSessionId(), createGameStateDto(gameSession));
        }
    }

    public GameStateDto getGameState(String sessionId) {
        GameSession gameSession = getGameSession(sessionId);
        return createGameStateDto(gameSession);
    }

    private void updateSubmissionProgress(GameSession gameSession, String type) {
        int submitted = type.equals("prompt") ? gameSession.getCurrentPrompts().size() : gameSession.getCurrentGuesses().size();
        int total = gameSession.getPlayers().size();
        messageUtil.updateSubmissionProgress(gameSession.getSessionId(), type, submitted, total);
    }
}