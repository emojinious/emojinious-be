package com.emojinious.emojinious_backend.model;

import com.emojinious.emojinious_backend.cache.Player;
import com.emojinious.emojinious_backend.constant.GamePhase;
import com.emojinious.emojinious_backend.constant.GameState;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import java.io.Serializable;
import java.util.*;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class GameSession implements Serializable {
    private String sessionId;
    private List<Player> players;
    private GameSettings settings;
    private GameState state;
    private int currentTurn;
    private GamePhase currentPhase;
    private Map<String, String> currentPrompts;

    private Map<String, Map<String, Float>> playerScores; // <나 <상대방, 내가 얻은 점수>>

    private List<Map<String, String>> currentGuesses;
    private Map<String, String> currentKeywords;
    private Map<String, String> generatedImages;
    private long phaseStartTime;
    private long phaseEndTime;
    private int currentGuessRound;
    private Map<String, Set<String>> guessedPlayers;
    private long currentRoundStartTime;
    private int currentRoundSubmittedGuesses;

    public GameSession() {
        // 역직렬화 문제 방지, 이제필요없음(아마도)
    }

    public GameSession(String sessionId) {
        this.sessionId = sessionId;
        this.players = new ArrayList<>();
        this.settings = new GameSettings();
        this.state = GameState.WAITING;
        this.currentTurn = 0;
        this.currentPhase = GamePhase.WAITING;
        this.currentPrompts = new HashMap<>();
        this.playerScores = new HashMap<>();
        this.currentGuesses = new ArrayList<>();
        this.currentKeywords = new HashMap<>();
        this.generatedImages = new HashMap<>();
        this.currentGuessRound = 0;
        this.guessedPlayers = new HashMap<>();
        this.currentRoundStartTime = 0;
    }

    public void addScore(String playerId, String targetPlayerId, float score) {
        playerScores.computeIfAbsent(playerId, k -> new HashMap<>())
                .put(targetPlayerId, score);
    }

    public void addPlayer(Player player) {
        if (players.size() >= 5) {
            throw new IllegalStateException("Maximum number of players reached");
        }
        players.add(player);
    }

    public boolean isHost(String playerId) {
        return players.get(0).getId().equals(playerId);
    }

    public void startGame() {
        if (players.size() < 2) {
            throw new IllegalStateException("Not enough players to start the game");
        }
        state = GameState.IN_PROGRESS;
        currentTurn = 1;
        moveToNextPhase();
    }

    public void moveToNextPhase() {
        System.out.println("GameSession.moveToNextPhase curr: " + currentPhase);
        switch (currentPhase) {
            case WAITING:
                currentPhase = GamePhase.LOADING;
                break;
            case LOADING:
                currentPhase = GamePhase.DESCRIPTION;
                break;
            case DESCRIPTION:
                currentPhase = GamePhase.GENERATION;
                break;
            case GENERATION:
                currentPhase = GamePhase.CHECKING;
                break;
            case CHECKING:
                currentPhase = GamePhase.GUESSING;
                break;
            case GUESSING:
                currentPhase = GamePhase.TURN_RESULT;
                break;
            case TURN_RESULT:
                if (currentTurn < settings.getTurns()) {
                    currentTurn++;
                    currentPhase = GamePhase.DESCRIPTION;
                } else {
                    currentPhase = GamePhase.RESULT;
                    state = GameState.FINISHED;
                }
                break;
            case RESULT:
                // Game is finished
                break;
        }
        startPhaseTimer();
    }

    public void startPhaseTimer() {
        System.out.println("GameSession.startPhaseTimer : " + currentPhase);
        phaseStartTime = System.currentTimeMillis();
        switch (currentPhase) {
            case DESCRIPTION -> phaseEndTime = phaseStartTime + (settings.getPromptTimeLimit() * 1000L);
            case GUESSING -> phaseEndTime = phaseStartTime + (settings.getGuessTimeLimit() * 1000L);
            case CHECKING -> phaseEndTime = phaseStartTime + 15 * 1000L;
            case TURN_RESULT -> phaseEndTime = phaseStartTime + 10 * 1000L;
            default -> phaseEndTime = phaseStartTime + 60 * 10 * 1000L;
        }
        System.out.println("Time: " + (phaseEndTime - phaseStartTime) + " ms");
    }

    public boolean isPhaseTimedOut() {
        return System.currentTimeMillis() > phaseEndTime;
    }

    public long getRemainingTime() {
        if (currentPhase == GamePhase.GUESSING && currentGuessRound > 0) {
            long totalGuessingTime = settings.getGuessTimeLimit() * 1000L;
            long elapsedTimeInCurrentRound = System.currentTimeMillis() - currentRoundStartTime;

            return Math.max(0, totalGuessingTime - elapsedTimeInCurrentRound);
        } else {
            return Math.max(0, phaseEndTime - System.currentTimeMillis());
        }
    }

    public Player getPlayerById(String playerId) {
        return players.stream()
                .filter(player -> player.getId().equals(playerId))
                .findFirst()
                .orElse(null);
    }

    public void removePlayer(String playerId) {
        players.removeIf(player -> player.getId().equals(playerId));
    }

    public void submitPrompt(String playerId, String prompt) {
        if (state != GameState.IN_PROGRESS) {
            throw new IllegalStateException("Game is not in progress");
        }
        currentPrompts.put(playerId, prompt);
    }

    public void saveImage(String playerId, String image) {
        if (state != GameState.IN_PROGRESS) {
            throw new IllegalStateException("Game is not in progress");
        }
        generatedImages.put(playerId, image);
    }

    public void submitGuess(String playerId, String guess) {
        if (currentPhase != GamePhase.GUESSING) {
            throw new IllegalStateException("Not in guessing phase");
        }
        String targetId = getGuessTargetForPlayer(playerId);
        Map<String, String> guessMap = new HashMap<>();
        guessMap.put("targetId", targetId);
        guessMap.put("guesserId", playerId);
        guessMap.put("guess", guess);
        currentGuesses.add(guessMap);
        System.out.println("currentGuesses = " + currentGuesses);

        guessedPlayers.computeIfAbsent(playerId, k -> new HashSet<>()).add(targetId);
        currentRoundSubmittedGuesses++;
//        if (currentGuesses.size() == players.size()) {
//            moveToNextPhase();
//        }
    }

    public void setGeneratedImage(String playerId, String imageUrl) {
        generatedImages.put(playerId, imageUrl);
    }


    public void startNewGuessRound() {
        currentGuessRound++;
//        currentGuesses.clear();
        currentRoundSubmittedGuesses = 0;
        players.forEach(player -> guessedPlayers.put(player.getId(), new HashSet<>()));
        currentRoundStartTime = System.currentTimeMillis();
    }

    public boolean areAllPlayersGuessedOrTimedOut(int guessTimeLimit) {
        return players.stream().allMatch(player ->
                hasPlayerGuessedAllOthers(player.getId()) || isCurrentRoundTimedOut(guessTimeLimit)
        );
    }

    private boolean isCurrentRoundTimedOut(int guessTimeLimit) {
        return System.currentTimeMillis() > currentRoundStartTime + guessTimeLimit * 1000L;
    }

    private boolean hasPlayerGuessedAllOthers(String playerId) {
        Set<String> guessedSet = this.guessedPlayers.get(playerId);
        return guessedSet != null && guessedSet.size() == players.size() - 1;
    }


    public String getGuessTargetForPlayer(String playerId) {
        List<String> playerIds = players.stream().map(Player::getId).toList();
        int playerIndex = playerIds.indexOf(playerId);
        int targetIndex = (playerIndex + currentGuessRound) % playerIds.size();
        return playerIds.get(targetIndex);
    }


    public boolean areAllImagesGenerated() {
        return generatedImages.size() == players.size();
    }

    public void clearCurrentRoundData() {
        currentPrompts.clear();
        currentGuesses.clear();
        generatedImages.clear();
    }
}