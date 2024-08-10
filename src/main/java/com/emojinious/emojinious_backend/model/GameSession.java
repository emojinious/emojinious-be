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
    private Map<String, String> currentGuesses;
    private Map<String, String> currentKeywords;
    private Map<String, String> generatedImages;
    private long phaseStartTime;
    private long phaseEndTime;

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
        this.currentGuesses = new HashMap<>();
        this.currentKeywords = new HashMap<>();
        this.generatedImages = new HashMap<>();
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
        switch (currentPhase) {
            case WAITING:
                currentPhase = GamePhase.DESCRIPTION;
                break;
            case DESCRIPTION:
                currentPhase = GamePhase.GENERATION;
                break;
            case GENERATION:
                currentPhase = GamePhase.GUESSING;
                break;
            case GUESSING:
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
        phaseStartTime = System.currentTimeMillis();
        switch (currentPhase) {
            case DESCRIPTION:
                phaseEndTime = phaseStartTime + (settings.getPromptTimeLimit() * 1000L);
                break;
            case GUESSING:
                phaseEndTime = phaseStartTime + (settings.getGuessTimeLimit() * 1000L);
                break;
            case GENERATION:
                phaseEndTime = phaseStartTime + 10000; // 10초
                break;
            default:
                phaseEndTime = phaseStartTime + 10000; // 10초
        }
    }

    public boolean isPhaseTimedOut() {
        return System.currentTimeMillis() > phaseEndTime;
    }

    public long getRemainingTime() {
        return Math.max(0, phaseEndTime - System.currentTimeMillis());
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
        if (currentPhase != GamePhase.DESCRIPTION) {
            throw new IllegalStateException("Not in description phase");
        }
        currentPrompts.put(playerId, prompt);
        if (currentPrompts.size() == players.size()) {
            moveToNextPhase();
        }
    }

    public void submitGuess(String playerId, String guess) {
        if (currentPhase != GamePhase.GUESSING) {
            throw new IllegalStateException("Not in guessing phase");
        }
        currentGuesses.put(playerId, guess);
        if (currentGuesses.size() == players.size()) {
            moveToNextPhase();
        }
    }

    public void setGeneratedImage(String playerId, String imageUrl) {
        generatedImages.put(playerId, imageUrl);
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