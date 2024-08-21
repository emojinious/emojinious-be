package com.emojinious.emojinious_backend.model;

import com.emojinious.emojinious_backend.cache.Player;
import com.emojinious.emojinious_backend.constant.GamePhase;
import com.emojinious.emojinious_backend.constant.GameState;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.stream.Collectors;
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
    private int currentGuessRound;
    private Map<String, Set<String>> guessedPlayers;
    private long currentRoundStartTime;

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
        this.currentGuessRound = 0;
        this.guessedPlayers = new HashMap<>();
        this.currentRoundStartTime = 0;
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
        phaseStartTime = System.currentTimeMillis();
        switch (currentPhase) {
            case DESCRIPTION:
                phaseEndTime = phaseStartTime + (settings.getPromptTimeLimit() * 1000L);
                break;
            case GUESSING:
                phaseEndTime = phaseStartTime + (settings.getGuessTimeLimit() * 1000L);
                break;
            case CHECKING:
                phaseEndTime = phaseStartTime + 10 * 1000;
                break;
            default:
                phaseEndTime = phaseStartTime + 60 * 10 * 1000;
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
        currentGuesses.put(playerId, guess);
        if (currentGuesses.size() == players.size()) {
            moveToNextPhase();
        }
    }

    public void setGeneratedImage(String playerId, String imageUrl) {
        generatedImages.put(playerId, imageUrl);
    }


    public void startNewGuessRound() {
        currentGuessRound++;
        currentGuesses.clear();
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