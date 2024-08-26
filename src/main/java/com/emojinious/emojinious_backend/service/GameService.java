package com.emojinious.emojinious_backend.service;

import com.emojinious.emojinious_backend.cache.Player;
import com.emojinious.emojinious_backend.dto.*;
import com.emojinious.emojinious_backend.model.*;
import com.emojinious.emojinious_backend.constant.GamePhase;
import com.emojinious.emojinious_backend.constant.GameState;
import com.emojinious.emojinious_backend.util.JwtUtil;
import com.emojinious.emojinious_backend.util.RedisUtil;
import com.emojinious.emojinious_backend.util.MessageUtil;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.*;
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
    private final Map<String, Set<String>> activeConnections = new ConcurrentHashMap<>();

    public void handleExistingConnection(String sessionId, String playerId) {
        Set<String> sessionPlayers = activeConnections.computeIfAbsent(sessionId, k -> ConcurrentHashMap.newKeySet());
        if (sessionPlayers.contains(playerId)) {
            // 기존 연결 종료 처리
            // messageUtil.sendDisconnectMessage(sessionId, playerId);
            sessionPlayers.remove(playerId);
        }
    }

    public boolean isPlayerAlreadyJoined(String sessionId, String playerId) {
        GameSession gameSession = getGameSession(sessionId);
        System.out.println(gameSession != null && gameSession.getPlayerById(playerId) != null);
        return gameSession != null && gameSession.getPlayerById(playerId) != null;
    }

    public GameStateDto joinGame(String sessionId, String playerId, String nickname) {
        GameSession gameSession = getOrCreateGameSession(sessionId);
        Player player = playerService.getPlayerById(playerId);
        gameSession.addPlayer(player);
        updateGameSession(gameSession);
        messageUtil.broadcastGameState(gameSession.getSessionId(), createGameStateDto(gameSession));
        return createGameStateDto(gameSession);
    }

    public GameStateDto startGame(String sessionId, String playerId) {
        System.out.println("GameService.startGame");
        GameSession gameSession = getGameSession(sessionId);
        if (!gameSession.isHost(playerId)) {
            throw new IllegalStateException("Only host can start the game");
        }
        gameSession.startGame();
        updateGameSession(gameSession);
        startLoadingPhase(gameSession);
        return createGameStateDto(gameSession);
    }

//    public void generateKeywords(GameSession gameSession) {
//        List<String> keywords = randomWordGenerator.getKeywordsFromTheme(gameSession.getSettings().getTheme(), gameSession.getPlayers().size());
//        gameSession.getCurrentKeywords().clear();
//        for (int i = 0; i < gameSession.getPlayers().size(); i++) {
//            Player player = gameSession.getPlayers().get(i);
//            if (i < keywords.size()) {
//                gameSession.getCurrentKeywords().put(player.getId(), keywords.get(i));
//            }
//        }
//        updateGameSession(gameSession);
//    }

    public GameStateDto submitPrompt(String sessionId, String playerId, String message) {
        System.out.println("GameService.submitPrompt");
        GameSession gameSession = getGameSession(sessionId);
        gameSession.submitPrompt(playerId, message);
        // String image = imageGenerator.getImagesFromMessage(message);
        // gameSession.saveImage(playerId, image);
        updateSubmissionProgress(gameSession, "prompt");
//        if (gameSession.getCurrentPrompts().size() == gameSession.getPlayers().size()) {
//            gameSession.moveToNextPhase();
//        }
        updateGameSession(gameSession);
        messageUtil.broadcastGameState(gameSession.getSessionId(), createGameStateDto(gameSession));
        System.out.println("gameSession = " + gameSession);
        return createGameStateDto(gameSession);
    }


    public GameStateDto submitGuess(String sessionId, String playerId, String guess) {
        GameSession gameSession = getGameSession(sessionId);
        gameSession.submitGuess(playerId, guess);
        updateSubmissionProgress(gameSession, "guess");
//        if (gameSession.getCurrentGuesses().size() == gameSession.getPlayers().size()) {
//            TurnResultDto turnResult = scoreCalculator.calculateScores(gameSession);
//            moveToNextPhase(gameSession);
//        }

        CompletableFuture.runAsync(() -> calculateAndSaveScore(gameSession, playerId, guess));

        updateGameSession(gameSession);
        messageUtil.broadcastGameState(gameSession.getSessionId(), createGameStateDto(gameSession));
        return createGameStateDto(gameSession);
    }

    private void calculateAndSaveScore(GameSession gameSession, String playerId, String guess) {
        String targetPlayerId = gameSession.getGuessTargetForPlayer(playerId);
        String targetKeyword = gameSession.getCurrentKeywords().get(targetPlayerId);
        float score = scoreCalculator.calculateSingleGuessScore(guess, targetKeyword);
        gameSession.addScore(playerId, targetPlayerId, score);
        updateGameSession(gameSession);
    }

    private void startLoadingPhase(GameSession gameSession) {
        System.out.println("GameService.startLoadingPhase");
        messageUtil.broadcastPhaseStartMessage(gameSession.getSessionId(), gameSession.getCurrentPhase(), "Keyword Generation");
        Map<String, String> keywords = randomWordGenerator.getKeywordsFromTheme(
                gameSession.getPlayers(),
                gameSession.getSettings().getTheme(),
                gameSession.getSettings().getDifficulty(),
                gameSession.getPlayers().size()
        );
        gameSession.getCurrentKeywords().clear();
        gameSession.getCurrentKeywords().putAll(keywords);
        updateGameSession(gameSession);
        moveToNextPhase(gameSession);
    }

    private void startDescriptionPhase(GameSession gameSession) {
        System.out.println("GameService.startDescriptionPhase");
        gameSession.getPlayers().forEach(player ->
                messageUtil.sendToPlayer(gameSession.getSessionId(), player.getSocketId(), "keyword", gameSession.getCurrentKeywords().get(player.getId())));
        messageUtil.broadcastGameState(gameSession.getSessionId(), createGameStateDto(gameSession));
        messageUtil.broadcastPhaseStartMessage(gameSession.getSessionId(), gameSession.getCurrentPhase(), "Description Phase");
        updateSubmissionProgress(gameSession, "prompt");
    }

    private void startGenerationPhase(GameSession gameSession) {
        System.out.println("GameService.startGenerationPhase");
        messageUtil.broadcastPhaseStartMessage(gameSession.getSessionId(), gameSession.getCurrentPhase(), "Image Generation");
        messageUtil.broadcastGameState(gameSession.getSessionId(), createGameStateDto(gameSession));

        List<CompletableFuture<Void>> futures = new ArrayList<>();

        System.out.println("gameSession = " + gameSession.getCurrentPrompts());

        gameSession.getCurrentPrompts().forEach((playerId, prompt) -> {
            //프롬프트 공백
            if(prompt == null || prompt.trim().isEmpty()){
                prompt = " ";
            }

            System.out.println("prompt = " + prompt);
            CompletableFuture<Void> future = imageGenerator.getImagesFromMessageAsync(prompt)
                    .thenAccept(imageUrl -> {
                        gameSession.setGeneratedImage(playerId, imageUrl);
                        System.out.println("img gen player: " + playerId);
                    });
            futures.add(future);
        });

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenRun(() -> {
                    System.out.println("all images generated");
                    updateGameSession(gameSession);
                    moveToNextPhase(gameSession);
                });
    }

    private void startCheckingPhase(GameSession gameSession){
        System.out.println("GameService.startCheckingPhase");
        messageUtil.broadcastGameState(gameSession.getSessionId(), createGameStateDto(gameSession));
        messageUtil.broadcastPhaseStartMessage(gameSession.getSessionId(), gameSession.getCurrentPhase(), "Checking Phase");

        Map<String, String> images = gameSession.getGeneratedImages();
        gameSession.getPlayers().forEach(player ->
                messageUtil.sendToPlayer(gameSession.getSessionId(), player.getSocketId(), "image", images.get(player.getId())));
        messageUtil.broadcastGameState(gameSession.getSessionId(), createGameStateDto(gameSession));
    }



    private void startGuessingPhase(GameSession gameSession) {
        System.out.println("GameService.startGuessingPhase");
        startNewGuessRound(gameSession);
//        messageUtil.broadcastGameState(gameSession.getSessionId(), createGameStateDto(gameSession));
//        messageUtil.broadcastPhaseStartMessage(gameSession.getSessionId(), gameSession.getCurrentPhase(), "Guessing Phase");
//        assignImagesToPlayers(gameSession);
//        messageUtil.broadcastGameState(gameSession.getSessionId(), createGameStateDto(gameSession));
    }

    private void assignImagesToPlayers(GameSession gameSession) {
        gameSession.getPlayers().forEach(player -> {
            String imageUrl = getNextImageForPlayer(gameSession, player.getId());
            messageUtil.sendToPlayer(gameSession.getSessionId(), player.getSocketId(), "image", imageUrl);
        });
    }

    private void startNewGuessRound(GameSession gameSession) {
        System.out.println("GameService.startNewGuessRound");
        gameSession.startNewGuessRound();
        updateGameSession(gameSession);
        assignImagesToPlayers(gameSession);
        messageUtil.broadcastPhaseStartMessage(gameSession.getSessionId(), gameSession.getCurrentPhase(),
                "Guessing Round " + gameSession.getCurrentGuessRound());
        updateSubmissionProgress(gameSession, "guess");
        messageUtil.broadcastGameState(gameSession.getSessionId(), createGameStateDto(gameSession));
    }

    private void startTurnResultPhase(GameSession gameSession) {
        System.out.println("GameService.startTurnResultPhase");
        messageUtil.broadcastGameState(gameSession.getSessionId(), createGameStateDto(gameSession));
        messageUtil.broadcastPhaseStartMessage(gameSession.getSessionId(), gameSession.getCurrentPhase(), "Turn Result Phase");

        TurnResultDto scores = scoreCalculator.calculateFinalScores(gameSession);

        System.out.println(scores);

        messageUtil.broadcastGameState(gameSession.getSessionId(), createGameStateDto(gameSession));
        messageUtil.broadcastGameResult(gameSession.getSessionId(), scores);

    }

    private void moveToNextPhase(GameSession gameSession) {
        gameSession.moveToNextPhase();
        updateGameSession(gameSession);
        switch (gameSession.getCurrentPhase()) {
            case DESCRIPTION:
                startDescriptionPhase(gameSession);
                break;
            case GENERATION:
                startGenerationPhase(gameSession);
                break;
            case CHECKING:
                startCheckingPhase(gameSession);
                break;
            case GUESSING:
                startGuessingPhase(gameSession);
                break;
            case TURN_RESULT:
                startTurnResultPhase(gameSession);
                break;
            case RESULT:
                endGame(gameSession);
                break;
        }
        updateGameSession(gameSession);
    }

    private void endGame(GameSession gameSession) {
        gameSession.setState(GameState.FINISHED);
        updateGameSession(gameSession);
//        gameSession.getPlayers().forEach(player ->
//                player.setScore(scores.get(player.getId())));
        messageUtil.broadcastGameState(gameSession.getSessionId(), createGameStateDto(gameSession));
        messageUtil.broadcastPhaseStartMessage(gameSession.getSessionId(), gameSession.getCurrentPhase(), "게임이 종료되었습니다. 결과를 확인해주세요.");

        redisTemplate.delete(GAME_SESSION_KEY + gameSession.getSessionId());
    }

    @Scheduled(fixedRate = 500)
    public void checkPhaseTimeouts() {
        List<String> sessionIds = Objects.requireNonNull(redisTemplate.keys(GAME_SESSION_KEY + "*")).stream()
                .map(key -> key.replace(GAME_SESSION_KEY, ""))
                .toList();

        for (String sessionId : sessionIds) {
            GameSession gameSession = getGameSession(sessionId);
            if (gameSession.getState() == GameState.IN_PROGRESS) {
                if (gameSession.getCurrentPhase() == GamePhase.GUESSING) {
                    handleGuessingPhaseTimeout(gameSession);
                } else if (gameSession.isPhaseTimedOut()) {
                    moveToNextPhase(gameSession);
                } else {
                    continue;
                }
//                messageUtil.broadcastGameState(gameSession.getSessionId(), createGameStateDto(gameSession));
            }
        }
    }

    private void handleGuessingPhaseTimeout(GameSession gameSession) {
        if (shouldMoveToNextRoundOrPhase(gameSession)) {
            if (gameSession.getCurrentGuessRound() < gameSession.getPlayers().size() - 1) {
                startNewGuessRound(gameSession);
            } else {
                moveToNextPhase(gameSession);
            }
        }
    }

    private boolean shouldMoveToNextRoundOrPhase(GameSession gameSession) {
        return gameSession.areAllPlayersGuessedOrTimedOut(gameSession.getSettings().getGuessTimeLimit());
    }

    private GameSession getOrCreateGameSession(String sessionId) {
        GameSession gameSession = redisUtil.get(GAME_SESSION_KEY + sessionId, GameSession.class);
        if (gameSession == null) {
            gameSession = new GameSession(sessionId);
            updateGameSession(gameSession);
        }
        return gameSession;
    }

    public GameSession getGameSession(String sessionId) {
        return redisUtil.get(GAME_SESSION_KEY + sessionId, GameSession.class);
    }

    private void updateGameSession(GameSession gameSession) {
        redisUtil.set(GAME_SESSION_KEY + gameSession.getSessionId(), gameSession);
    }

    public void broadcastChatMessage(String sessionId, ChatMessage message) {
        messageUtil.broadcastChatMessage(sessionId, message);
    }


    // TODO: guessing 일 때 이것저것 정보 추가
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

//        if (gameSession.getCurrentPhase() == GamePhase.DESCRIPTION) {
//            dto.setCurrentPrompts(gameSession.getCurrentPrompts());
//        } else if (gameSession.getCurrentPhase() == GamePhase.GUESSING) {
//            dto.setCurrentGuesses(gameSession.getCurrentGuesses());
//        }

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

    // 굳이?
    private GameSettingsDto convertToGameSettingsDto(GameSettings settings) {
        GameSettingsDto dto = new GameSettingsDto();
        dto.setPromptTimeLimit(settings.getPromptTimeLimit());
        dto.setGuessTimeLimit(settings.getGuessTimeLimit());
        dto.setDifficulty(settings.getDifficulty());
        dto.setTurns(settings.getTurns());
        dto.setTheme(settings.getTheme());
        return dto;
    }

    private String getNextImageForPlayer(GameSession gameSession, String playerId) {
        String targetPlayerId = gameSession.getGuessTargetForPlayer(playerId);
        return gameSession.getGeneratedImages().get(targetPlayerId);
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
        System.out.println("GameService.handlePlayerConnect");
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
        updateGameSession(gameSession);
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
        int submitted;
        if (type.equals("prompt")) {
            submitted = gameSession.getCurrentPrompts().size();
        } else { // guess
            submitted = gameSession.getCurrentRoundSubmittedGuesses();
        }
        int total = gameSession.getPlayers().size();
        messageUtil.updateSubmissionProgress(gameSession.getSessionId(), type, submitted, total);
    }
}

//turn result 프론트로 보내주기
//다른사람의 평균값 자신의 점수로 더하기