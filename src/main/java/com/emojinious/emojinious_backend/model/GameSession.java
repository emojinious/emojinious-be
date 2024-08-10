package com.emojinious.emojinious_backend.model;

import com.emojinious.emojinious_backend.cache.Player;
import com.emojinious.emojinious_backend.constant.GameState;
import com.emojinious.emojinious_backend.service.GameService;
import com.emojinious.emojinious_backend.service.GenerateKeywordService;
import lombok.Data;
import java.io.Serializable;
import java.util.*;

@Data
public class GameSession implements Serializable {
    private String sessionId;
    private List<Player> players;
    private GameSettings settings;
    private GameState state;
    private int currentTurn;
    private Map<String, String> currentPrompts;
    private Map<String, String> currentGuesses;
    private Map<String, String> currentKeywords;
    private Map<String, String> currentImages;
    private long turnStartTime;
    private long turnEndTime;

    public GameSession() {
        // 역직렬화 문제 방지
    }

    public GameSession(String sessionId) {
        this.sessionId = sessionId;
        this.players = new ArrayList<>();
        this.settings = new GameSettings();
        this.state = GameState.WAITING;
        this.currentTurn = 0;
        this.currentPrompts = new HashMap<>();
        this.currentGuesses = new HashMap<>();
        this.currentKeywords = new HashMap<>();
        this.currentImages = new HashMap<>();
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
        startTurnTimer();
    }


    public void submitPrompt(String playerId, String prompt) {
//        if (state != GameState.IN_PROGRESS) {
//            throw new IllegalStateException("Game is not in progress");
//        }
        currentPrompts.put(playerId, prompt);
    }

    public void saveImage(String playerId, String image) {
//        if (state != GameState.IN_PROGRESS) {
//            throw new IllegalStateException("Game is not in progress");
//        }
        currentImages.put(playerId, image);
    }

    public void submitGuess(String playerId, String guess) {
        if (state != GameState.IN_PROGRESS) {
            throw new IllegalStateException("Game is not in progress");
        }
        currentGuesses.put(playerId, guess);
        if (currentGuesses.size() == players.size()) {
            moveToNextTurn();
        }
    }

    private void startTurnTimer() {
        turnStartTime = System.currentTimeMillis();
        turnEndTime = turnStartTime + (settings.getPromptTimeLimit() * 1000);
    }

    public void moveToGuessingPhase() {
        this.turnStartTime = System.currentTimeMillis();
        this.turnEndTime = turnStartTime + (settings.getGuessTimeLimit() * 1000);
        // 추가 ...
    }

    public void moveToNextTurn() {
        this.currentTurn++;
        if (this.currentTurn > this.settings.getTurns()) {
            this.state = GameState.FINISHED;
        } else {
            this.currentPrompts.clear();
            this.currentGuesses.clear();
            this.turnStartTime = System.currentTimeMillis();
            this.turnEndTime = turnStartTime + (settings.getPromptTimeLimit() * 1000);
        }
    }

    public Player getPlayerById(String playerId) {
        return players.stream()
                .filter(player -> player.getId().equals(playerId))
                .findFirst()
                .orElse(null);
    }

    public void removePlayer(String playerId) {
    }
}